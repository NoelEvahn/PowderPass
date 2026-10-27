'use server'

import { revalidatePath } from 'next/cache'

import { holdProduct, releaseExpired, releaseFromCart } from '@/lib/commerce/cart'
import { getPayloadClient } from '@/lib/payload'
import { rateLimit } from '@/lib/rate-limit'
import { ensureCartId, getCartId, getClientIP, getCustomer } from '@/lib/session'

import type { ActionState } from './types'

const formatUntil = (iso: string) =>
  new Date(iso).toLocaleString('en-MY', {
    timeZone: 'Asia/Kuala_Lumpur',
    weekday: 'short',
    day: 'numeric',
    month: 'short',
    hour: 'numeric',
    minute: '2-digit',
  })

export async function addToCartAction(_prev: ActionState, form: FormData): Promise<ActionState> {
  const productId = Number(form.get('productId'))
  if (!Number.isInteger(productId) || productId <= 0) return { status: 'error', message: 'That piece could not be found.' }

  const ip = await getClientIP()
  if (!rateLimit(`cart:${ip}`, 30, 10 * 60_000).ok) {
    return { status: 'error', message: 'Too many attempts. Please wait a few minutes and try again.' }
  }

  const payload = await getPayloadClient()
  await releaseExpired(payload)
  const cartId = await ensureCartId()
  const customer = await getCustomer()
  const result = await holdProduct(payload, { productId, cartId, customerId: customer?.id })

  revalidatePath('/', 'layout')
  if (result.ok) {
    return {
      status: 'success',
      message: result.alreadyHeld
        ? `Already in your cart. Held for you until ${formatUntil(result.expiresAt)}.`
        : `Added to your cart. Held for you until ${formatUntil(result.expiresAt)}.`,
    }
  }
  const messages: Record<typeof result.reason, string> = {
    not_found: 'That piece could not be found.',
    unavailable: 'Sorry, this piece has just sold.',
    not_online: 'This piece is available in the boutique only. Message us to ask about it.',
    held: `Someone is holding this piece${result.until ? ` until ${formatUntil(result.until)}` : ''}. It may come back if they do not buy it.`,
    cart_full: 'Your cart is full. Check out or remove a piece before adding another.',
  }
  return { status: 'error', message: messages[result.reason] }
}

export async function removeFromCartAction(form: FormData): Promise<void> {
  const productId = Number(form.get('productId'))
  const cartId = await getCartId()
  if (!cartId || !Number.isInteger(productId)) return
  const payload = await getPayloadClient()
  await releaseFromCart(payload, { productId, cartId })
  revalidatePath('/', 'layout')
}
