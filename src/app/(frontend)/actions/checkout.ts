'use server'

import { revalidatePath } from 'next/cache'
import { redirect } from 'next/navigation'

import { cancelUnpaidOrder, checkout, findOrderForViewer, orderPath } from '@/lib/commerce/orders'
import { getPayloadClient } from '@/lib/payload'
import { rateLimit } from '@/lib/rate-limit'
import { getCartId, getClientIP, getCustomer } from '@/lib/session'
import { checkoutSchema, formToObject, zodFieldErrors } from '@/lib/validation'

import type { ActionState } from './types'

export async function checkoutAction(_prev: ActionState, form: FormData): Promise<ActionState> {
  const raw = formToObject(form)
  const values = { ...raw }
  delete values.agree

  const ip = await getClientIP()
  if (!rateLimit(`checkout:${ip}`, 10, 10 * 60_000).ok) {
    return { status: 'error', message: 'Too many attempts. Please wait a few minutes and try again.', values }
  }

  const parsed = checkoutSchema.safeParse(raw)
  if (!parsed.success) {
    return { status: 'error', message: 'Please check the highlighted fields.', errors: zodFieldErrors(parsed.error), values }
  }
  const cartId = await getCartId()
  if (!cartId) return { status: 'error', message: 'Your cart is empty or your hold has ended.', values }

  const d = parsed.data
  const payload = await getPayloadClient()
  const customer = await getCustomer()
  const result = await checkout(payload, {
    cartId,
    customerId: customer?.id,
    input: {
      name: d.name,
      email: d.email,
      phone: d.phone,
      paymentMethod: d.paymentMethod,
      fulfilment: d.fulfilment,
      address:
        d.fulfilment === 'delivery'
          ? { line1: d.line1!, line2: d.line2, postcode: d.postcode!, city: d.city!, state: d.state! }
          : undefined,
      note: d.note,
    },
  })

  if (!result.ok) {
    revalidatePath('/', 'layout')
    return { status: 'error', message: result.error, values }
  }
  revalidatePath('/', 'layout')
  redirect(result.redirectTo)
}

export async function cancelOrderAction(form: FormData): Promise<void> {
  const orderNumber = String(form.get('orderNumber') ?? '')
  const token = String(form.get('token') ?? '')
  const payload = await getPayloadClient()
  const customer = await getCustomer()
  const order = await findOrderForViewer(payload, { orderNumber, token, customerId: customer?.id })
  if (!order) redirect('/')
  await cancelUnpaidOrder(payload, order)
  revalidatePath('/', 'layout')
  redirect(`${orderPath(order)}&cancelled=1`)
}
