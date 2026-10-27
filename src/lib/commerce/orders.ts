import { randomBytes } from 'crypto'
import type { Payload, PayloadRequest } from 'payload'

import { toSen } from '@/lib/catalog'
import { getBillplzConfig, getServerURL } from '@/lib/env'
import type { Brand, Order } from '@/payload-types'

import { createBill, deleteBill } from './billplz'
import { getCartItems, releaseExpired } from './cart'
import { sendOrderEmails } from './notify'
import { getShopSettings } from './settings'

const SKIP = { skipOrderSync: true }

export type CheckoutInput = {
  name: string
  email: string
  phone: string
  paymentMethod: 'online' | 'pay_at_shop'
  fulfilment: 'pickup' | 'delivery'
  address?: { line1: string; line2?: string; postcode: string; city: string; state: string }
  note?: string
}

export type CheckoutResult =
  | { ok: true; redirectTo: string; order: Order }
  | { ok: false; error: string; code: 'empty' | 'unavailable' | 'method' | 'payment_provider' }

const ORDER_ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'

export const generateOrderNumber = (date = new Date()): string => {
  const kl = new Date(date.getTime() + 8 * 3_600_000) // Malaysia time, no DST
  const ymd = kl.toISOString().slice(2, 10).replace(/-/g, '')
  const bytes = randomBytes(5)
  const suffix = Array.from(bytes, (b) => ORDER_ALPHABET[b % ORDER_ALPHABET.length]).join('')
  return `LT${ymd}-${suffix}`
}

export const orderPath = (order: Pick<Order, 'orderNumber' | 'accessToken'>) =>
  `/order/${encodeURIComponent(order.orderNumber)}?t=${encodeURIComponent(order.accessToken)}`

const productIdsOf = (order: Order): number[] =>
  order.items
    .map((i) => (typeof i.product === 'object' && i.product ? i.product.id : i.product))
    .filter((id): id is number => typeof id === 'number')

// Serialises payment confirmation per order inside this process, so the redirect
// and the callback arriving together cannot both send receipts.
const locks = new Map<string, Promise<unknown>>()
const withLock = async <T>(key: string, fn: () => Promise<T>): Promise<T> => {
  const previous = locks.get(key) ?? Promise.resolve()
  let release: () => void = () => {}
  const current = new Promise<void>((r) => (release = r))
  const chained = previous.then(() => current)
  locks.set(key, chained)
  await previous
  try {
    return await fn()
  } finally {
    release()
    if (locks.get(key) === chained) locks.delete(key)
  }
}

export const checkout = async (
  payload: Payload,
  { cartId, customerId, input }: { cartId: string; customerId?: number | null; input: CheckoutInput },
): Promise<CheckoutResult> => {
  await releaseExpired(payload, { force: true })
  const settings = await getShopSettings(payload)

  if (input.paymentMethod === 'online' && !settings.onlinePayment) {
    return { ok: false, code: 'method', error: 'Online payment is not available right now. Please choose to pay at the boutique.' }
  }
  if (input.paymentMethod === 'pay_at_shop' && !settings.payAtShop) {
    return { ok: false, code: 'method', error: 'Paying at the boutique is not available right now.' }
  }
  if (input.fulfilment === 'delivery' && (!settings.delivery || input.paymentMethod !== 'online')) {
    return { ok: false, code: 'method', error: 'Delivery is only available with online payment.' }
  }

  const items = await getCartItems(payload, cartId)
  if (!items.length) return { ok: false, code: 'empty', error: 'Your cart is empty or your hold has ended.' }
  const unavailable = items.filter((i) => i.product.status !== 'available' || i.product._status !== 'published')
  if (unavailable.length) {
    for (const i of unavailable) await payload.delete({ collection: 'reservations', id: i.reservationId })
    return {
      ok: false,
      code: 'unavailable',
      error: `Sorry, ${unavailable.map((i) => i.product.title).join(', ')} is no longer available and has been removed from your cart.`,
    }
  }

  const now = Date.now()
  const earliestHold = Math.min(...items.map((i) => new Date(i.expiresAt).getTime()))
  const holdExpiresAt = new Date(
    input.paymentMethod === 'pay_at_shop'
      ? now + settings.payAtShopHoldHours * 3_600_000
      : Math.max(earliestHold, now + settings.paymentWindowMinutes * 60_000),
  ).toISOString()

  const subtotal = items.reduce((sum, i) => sum + i.product.price, 0)
  const deliveryFee = input.fulfilment === 'delivery' ? settings.deliveryFee : 0

  let order: Order | null = null
  for (let attempt = 0; attempt < 3 && !order; attempt++) {
    try {
      order = await payload.create({
        collection: 'orders',
        context: SKIP,
        data: {
          orderNumber: generateOrderNumber(),
          accessToken: randomBytes(24).toString('base64url'),
          status: input.paymentMethod === 'online' ? 'pending_payment' : 'reserved',
          paymentMethod: input.paymentMethod,
          fulfilment: input.fulfilment,
          holdExpiresAt,
          items: items.map((i) => ({
            product: i.product.id,
            title: i.product.title,
            brand: typeof i.product.brand === 'object' ? (i.product.brand as Brand).name : null,
            price: i.product.price,
          })),
          subtotal,
          deliveryFee,
          total: subtotal + deliveryFee,
          contact: { name: input.name, email: input.email, phone: input.phone },
          deliveryAddress: input.fulfilment === 'delivery' ? input.address : undefined,
          customerNote: input.note || undefined,
          customer: customerId ?? undefined,
          payment: input.paymentMethod === 'online' ? { provider: 'billplz' } : undefined,
        },
      })
    } catch (err) {
      if (attempt === 2) throw err
    }
  }
  if (!order) throw new Error('Order could not be created')

  const previous = items.map((i) => ({ id: i.reservationId, expiresAt: i.expiresAt }))
  for (const i of items) {
    await payload.update({
      collection: 'reservations',
      id: i.reservationId,
      data: { order: order.id, cartId: null, expiresAt: holdExpiresAt, customer: customerId ?? undefined },
    })
  }

  if (input.paymentMethod === 'pay_at_shop') {
    await sendOrderEmails(payload, order, 'reserved')
    return { ok: true, order, redirectTo: orderPath(order) }
  }

  const cfg = getBillplzConfig()!
  try {
    const bill = await createBill(cfg, {
      orderNumber: order.orderNumber,
      amountSen: toSen(order.total),
      name: input.name,
      email: input.email,
      phone: input.phone,
      callbackURL: `${getServerURL()}/payments/billplz/callback`,
      redirectURL: `${getServerURL()}${orderPath(order)}`,
    })
    order = await payload.update({
      collection: 'orders',
      id: order.id,
      context: SKIP,
      data: { payment: { provider: 'billplz', billId: bill.id, billUrl: bill.url } },
    })
    return { ok: true, order, redirectTo: bill.url }
  } catch (err) {
    payload.logger.error({ err, order: order.orderNumber }, 'Billplz bill could not be created')
    // Put the pieces back in the cart so the customer can try again or pay at the boutique.
    for (const p of previous) {
      await payload.update({
        collection: 'reservations',
        id: p.id,
        data: { order: null, cartId, expiresAt: p.expiresAt },
      })
    }
    await payload.update({
      collection: 'orders',
      id: order.id,
      context: SKIP,
      data: { status: 'cancelled', staffNotes: 'Cancelled automatically: the payment provider did not respond.' },
    })
    return {
      ok: false,
      code: 'payment_provider',
      error: 'We could not reach the payment provider. Your pieces are still held; please try again in a moment.',
    }
  }
}

const markProductsSold = async (payload: Payload, ids: number[], req?: PayloadRequest) => {
  for (const id of ids) {
    await payload.update({ collection: 'products', id, data: { status: 'sold' }, req, context: SKIP })
  }
}

const releaseOrderHolds = async (payload: Payload, orderId: number, req?: PayloadRequest) => {
  await payload.delete({ collection: 'reservations', where: { order: { equals: orderId } }, req })
}

export type PaymentConfirmation = { billId: string; paidAt?: string | null; transactionId?: string | null; amountSen?: number | null }

/**
 * Records a successful online payment. Safe to call more than once (callback and
 * redirect both call it). A payment that arrives after the hold ended still
 * succeeds if the pieces are free; otherwise the order is flagged for a refund.
 */
export const confirmOnlinePayment = async (payload: Payload, confirmation: PaymentConfirmation): Promise<Order | null> => {
  const { docs } = await payload.find({
    collection: 'orders',
    where: { 'payment.billId': { equals: confirmation.billId } },
    depth: 0,
    limit: 1,
  })
  const found = docs[0]
  if (!found) {
    payload.logger.warn({ billId: confirmation.billId }, 'Payment for unknown bill')
    return null
  }

  return withLock(`order:${found.id}`, async () => {
    const order = await payload.findByID({ collection: 'orders', id: found.id, depth: 0 })
    if (['paid', 'completed', 'refund_due'].includes(order.status)) return order

    const payment = {
      ...order.payment,
      transactionId: confirmation.transactionId || order.payment?.transactionId || undefined,
      paidAt: confirmation.paidAt ? new Date(confirmation.paidAt).toISOString() : new Date().toISOString(),
    }

    if (confirmation.amountSen != null && confirmation.amountSen !== toSen(order.total)) {
      const flagged = await payload.update({
        collection: 'orders',
        id: order.id,
        context: SKIP,
        data: { status: 'refund_due', payment, staffNotes: `Amount paid (${confirmation.amountSen} sen) does not match the order total. Check in Billplz.` },
      })
      await sendOrderEmails(payload, flagged, 'refund_due')
      return flagged
    }

    const ids = productIdsOf(order)
    if (order.status !== 'pending_payment') {
      // Paid after the hold ended or after cancelling: only keep the sale if every piece is still free.
      const products = await payload.find({ collection: 'products', where: { id: { in: ids } }, depth: 0, limit: ids.length, pagination: false })
      const holds = await payload.find({ collection: 'reservations', where: { product: { in: ids } }, depth: 0, limit: ids.length, pagination: false })
      const blocked =
        products.docs.length !== ids.length ||
        products.docs.some((p) => p.status !== 'available') ||
        holds.docs.some((h) => (typeof h.order === 'object' ? h.order?.id : h.order) !== order.id && new Date(h.expiresAt).getTime() > Date.now())
      if (blocked) {
        const flagged = await payload.update({
          collection: 'orders',
          id: order.id,
          context: SKIP,
          data: { status: 'refund_due', payment, staffNotes: 'Payment arrived after the hold ended and a piece was no longer available.' },
        })
        await sendOrderEmails(payload, flagged, 'refund_due')
        return flagged
      }
      await payload.delete({ collection: 'reservations', where: { product: { in: ids } } })
    }

    const paid = await payload.update({
      collection: 'orders',
      id: order.id,
      context: SKIP,
      data: { status: 'paid', payment },
    })
    await markProductsSold(payload, ids)
    await releaseOrderHolds(payload, order.id)
    await sendOrderEmails(payload, paid, 'paid')
    return paid
  })
}

export const expireOrder = async (payload: Payload, order: Order) => {
  return withLock(`order:${order.id}`, async () => {
    const fresh = await payload.findByID({ collection: 'orders', id: order.id, depth: 0 })
    if (!['pending_payment', 'reserved'].includes(fresh.status)) return fresh
    const expired = await payload.update({ collection: 'orders', id: order.id, context: SKIP, data: { status: 'expired' } })
    await releaseOrderHolds(payload, order.id)
    const cfg = getBillplzConfig()
    if (cfg && fresh.payment?.billId) await deleteBill(cfg, fresh.payment.billId).catch(() => undefined)
    await sendOrderEmails(payload, expired, 'expired')
    return expired
  })
}

/** Customer cancels an unpaid order from the order page. */
export const cancelUnpaidOrder = async (payload: Payload, order: Order): Promise<Order> => {
  return withLock(`order:${order.id}`, async () => {
    const fresh = await payload.findByID({ collection: 'orders', id: order.id, depth: 0 })
    if (!['pending_payment', 'reserved'].includes(fresh.status)) return fresh
    const cancelled = await payload.update({ collection: 'orders', id: order.id, context: SKIP, data: { status: 'cancelled' } })
    await releaseOrderHolds(payload, order.id)
    const cfg = getBillplzConfig()
    if (cfg && fresh.payment?.billId) await deleteBill(cfg, fresh.payment.billId).catch(() => undefined)
    await sendOrderEmails(payload, cancelled, 'cancelled')
    return cancelled
  })
}

/** Keeps stock and holds in step when staff change an order's status in the editor. */
export const syncOrderStatusChange = async (req: PayloadRequest, order: Order, previous?: Order['status']) => {
  const { payload } = req
  const ids = productIdsOf(order)
  switch (order.status) {
    case 'paid':
    case 'completed':
      await markProductsSold(payload, ids, req)
      await releaseOrderHolds(payload, order.id, req)
      if (order.status === 'paid' && !order.payment?.paidAt) {
        await payload.update({
          collection: 'orders',
          id: order.id,
          req,
          context: SKIP,
          data: { payment: { ...order.payment, paidAt: new Date().toISOString() } },
        })
      }
      break
    case 'cancelled':
    case 'expired':
      await releaseOrderHolds(payload, order.id, req)
      if (previous === 'paid' || previous === 'completed') {
        // A refunded or cancelled sale: the pieces return to the shop.
        for (const id of ids) {
          await payload.update({ collection: 'products', id, req, context: SKIP, data: { status: 'available' } })
        }
      }
      break
    default:
      break
  }
}

/** Server-side lookup for the public order page: number and secret token must both match. */
export const findOrderForViewer = async (
  payload: Payload,
  { orderNumber, token, customerId }: { orderNumber: string; token?: string | null; customerId?: number | null },
): Promise<Order | null> => {
  const { docs } = await payload.find({
    collection: 'orders',
    where: { orderNumber: { equals: orderNumber } },
    depth: 1,
    limit: 1,
  })
  const order = docs[0]
  if (!order) return null
  const ownerId = typeof order.customer === 'object' ? order.customer?.id : order.customer
  const { timingSafeEqual } = await import('crypto')
  const tokenOk =
    typeof token === 'string' &&
    token.length === order.accessToken.length &&
    timingSafeEqual(Buffer.from(token), Buffer.from(order.accessToken))
  if (tokenOk || (customerId && ownerId === customerId)) return order
  return null
}
