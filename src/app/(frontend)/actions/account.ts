'use server'

import { cookies } from 'next/headers'
import { redirect } from 'next/navigation'
import { createLocalReq, logoutOperation } from 'payload'

import { attachCartToCustomer } from '@/lib/commerce/cart'
import { isProduction } from '@/lib/env'
import { getPayloadClient } from '@/lib/payload'
import { rateLimit } from '@/lib/rate-limit'
import { getCartId, getClientIP, getCustomer, getViewer } from '@/lib/session'
import {
  formToObject,
  loginSchema,
  profileSchema,
  registerSchema,
  resetSchema,
  zodFieldErrors,
  emailSchema,
} from '@/lib/validation'

import type { ActionState } from './types'

const safeNext = (value: FormDataEntryValue | null): string => {
  const v = typeof value === 'string' ? value : ''
  return v.startsWith('/') && !v.startsWith('//') && !v.startsWith('/admin') ? v : '/account'
}

const setSessionCookie = async (token: string, exp?: number) => {
  const payload = await getPayloadClient()
  ;(await cookies()).set(`${payload.config.cookiePrefix}-token`, token, {
    httpOnly: true,
    sameSite: 'lax',
    secure: isProduction,
    path: '/',
    expires: exp ? new Date(exp * 1000) : undefined,
  })
}

const linkCart = async (customerId: number) => {
  const cartId = await getCartId()
  if (!cartId) return
  const payload = await getPayloadClient()
  await attachCartToCustomer(payload, cartId, customerId).catch(() => undefined)
}

const tooMany: ActionState = { status: 'error', message: 'Too many attempts. Please wait a few minutes and try again.' }

export async function loginAction(_prev: ActionState, form: FormData): Promise<ActionState> {
  const raw = formToObject(form)
  const values = { email: raw.email ?? '' }
  if (!rateLimit(`login:${await getClientIP()}`, 10, 15 * 60_000).ok) return { ...tooMany, values }

  const parsed = loginSchema.safeParse(raw)
  if (!parsed.success) return { status: 'error', message: 'Please check the highlighted fields.', errors: zodFieldErrors(parsed.error), values }

  const payload = await getPayloadClient()
  try {
    const result = await payload.login({ collection: 'customers', data: parsed.data })
    if (!result.token || !result.user) throw new Error('No token')
    await setSessionCookie(result.token, result.exp)
    await linkCart(result.user.id)
  } catch (err) {
    const locked = err instanceof Error && /locked/i.test(err.message)
    return {
      status: 'error',
      message: locked
        ? 'This account is locked for 15 minutes after too many attempts. Try again later or reset your password.'
        : 'That email and password do not match an account.',
      values,
    }
  }
  redirect(safeNext(form.get('next')))
}

export async function registerAction(_prev: ActionState, form: FormData): Promise<ActionState> {
  const raw = formToObject(form)
  const values = { name: raw.name ?? '', email: raw.email ?? '', phone: raw.phone ?? '' }
  if (form.get('website')) return { status: 'success', message: 'Thank you.' }
  if (!rateLimit(`register:${await getClientIP()}`, 5, 60 * 60_000).ok) return { ...tooMany, values }

  const parsed = registerSchema.safeParse(raw)
  if (!parsed.success) return { status: 'error', message: 'Please check the highlighted fields.', errors: zodFieldErrors(parsed.error), values }

  const payload = await getPayloadClient()
  const { totalDocs } = await payload.count({ collection: 'customers', where: { email: { equals: parsed.data.email } } })
  if (totalDocs) {
    return {
      status: 'error',
      message: 'An account with this email already exists. Sign in, or reset your password if you have forgotten it.',
      errors: { email: 'This email already has an account.' },
      values,
    }
  }
  try {
    await payload.create({
      collection: 'customers',
      data: {
        name: parsed.data.name,
        email: parsed.data.email,
        phone: parsed.data.phone || undefined,
        password: parsed.data.password,
        marketingOptIn: parsed.data.marketingOptIn === 'on',
      },
    })
    const result = await payload.login({
      collection: 'customers',
      data: { email: parsed.data.email, password: parsed.data.password },
    })
    if (result.token) await setSessionCookie(result.token, result.exp)
    if (result.user) await linkCart(result.user.id)
  } catch (err) {
    payload.logger.error({ err }, 'Customer registration failed')
    return { status: 'error', message: 'We could not create your account. Please try again.', values }
  }
  redirect(safeNext(form.get('next')))
}

export async function logoutAction(): Promise<void> {
  const payload = await getPayloadClient()
  const user = await getViewer()
  if (user && user.collection === 'customers') {
    try {
      const req = await createLocalReq({ user }, payload)
      await logoutOperation({ collection: payload.collections.customers, req })
    } catch {
      // The cookie is removed below either way.
    }
  }
  if (!user || user.collection === 'customers') {
    ;(await cookies()).delete(`${payload.config.cookiePrefix}-token`)
  }
  redirect('/')
}

export async function forgotPasswordAction(_prev: ActionState, form: FormData): Promise<ActionState> {
  const email = String(form.get('email') ?? '')
  if (!rateLimit(`forgot:${await getClientIP()}`, 5, 60 * 60_000).ok) return { ...tooMany, values: { email } }
  const parsed = emailSchema.safeParse(email)
  if (!parsed.success) {
    return { status: 'error', errors: { email: parsed.error.issues[0]?.message ?? 'Enter a valid email address.' }, values: { email } }
  }
  const payload = await getPayloadClient()
  try {
    await payload.forgotPassword({ collection: 'customers', data: { email: parsed.data } })
  } catch (err) {
    payload.logger.warn({ err }, 'Forgot password request failed')
  }
  // Same answer whether or not the account exists.
  return {
    status: 'success',
    message: 'If an account uses that email, we have sent a link to reset the password. It works for one hour.',
  }
}

export async function resetPasswordAction(_prev: ActionState, form: FormData): Promise<ActionState> {
  if (!rateLimit(`reset:${await getClientIP()}`, 10, 60 * 60_000).ok) return tooMany
  const parsed = resetSchema.safeParse(formToObject(form))
  if (!parsed.success) return { status: 'error', message: 'Please check the highlighted fields.', errors: zodFieldErrors(parsed.error) }

  const payload = await getPayloadClient()
  try {
    const result = await payload.resetPassword({
      collection: 'customers',
      data: { token: parsed.data.token, password: parsed.data.password },
      overrideAccess: true,
    })
    if (result.token) await setSessionCookie(result.token)
  } catch {
    return { status: 'error', message: 'This reset link has expired or was already used. Ask for a new one.' }
  }
  redirect('/account?reset=1')
}

export async function updateProfileAction(_prev: ActionState, form: FormData): Promise<ActionState> {
  const customer = await getCustomer()
  if (!customer) redirect('/account/sign-in')
  const raw = formToObject(form)
  const parsed = profileSchema.safeParse(raw)
  if (!parsed.success) return { status: 'error', message: 'Please check the highlighted fields.', errors: zodFieldErrors(parsed.error), values: raw }

  const payload = await getPayloadClient()
  await payload.update({
    collection: 'customers',
    id: customer.id,
    data: { name: parsed.data.name, phone: parsed.data.phone || null, marketingOptIn: parsed.data.marketingOptIn === 'on' },
  })
  return { status: 'success', message: 'Your details are saved.' }
}
