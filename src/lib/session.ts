import 'server-only'

import { randomUUID } from 'crypto'
import { cookies, headers } from 'next/headers'
import { cache } from 'react'

import { isProduction } from '@/lib/env'
import { getPayloadClient } from '@/lib/payload'
import type { Customer } from '@/payload-types'

export const CART_COOKIE = 'lt_cart'
const CART_MAX_AGE = 60 * 60 * 24

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

/** The cart id from the cookie, if there is a valid one. Read-only: safe in server components. */
export const getCartId = async (): Promise<string | null> => {
  const value = (await cookies()).get(CART_COOKIE)?.value
  return value && UUID_RE.test(value) ? value : null
}

/** Returns the cart id, creating the cookie if needed. Only call from server actions or route handlers. */
export const ensureCartId = async (): Promise<string> => {
  const existing = await getCartId()
  const id = existing ?? randomUUID()
  ;(await cookies()).set(CART_COOKIE, id, {
    httpOnly: true,
    sameSite: 'lax',
    secure: isProduction,
    path: '/',
    maxAge: CART_MAX_AGE,
  })
  return id
}

/** The signed-in user (staff or customer) for this request, cached per render. */
export const getViewer = cache(async () => {
  const payload = await getPayloadClient()
  try {
    const { user } = await payload.auth({ headers: await headers() })
    return user ?? null
  } catch {
    return null
  }
})

export const getCustomer = cache(async (): Promise<Customer | null> => {
  const user = await getViewer()
  return user && user.collection === 'customers' ? (user as Customer) : null
})

export const getClientIP = async (): Promise<string> => {
  const h = await headers()
  return (h.get('x-forwarded-for')?.split(',')[0] || h.get('x-real-ip') || 'unknown').trim()
}
