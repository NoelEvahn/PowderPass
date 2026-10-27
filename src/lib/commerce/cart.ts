import type { Payload, Where } from 'payload'

import type { Media, Product, Reservation } from '@/payload-types'

import { getShopSettings } from './settings'

export type CartItem = {
  reservationId: number
  expiresAt: string
  product: Product & { gallery: (number | Media)[] }
}

export type HoldResult =
  | { ok: true; expiresAt: string; alreadyHeld?: boolean }
  | { ok: false; reason: 'not_found' | 'unavailable' | 'not_online' | 'held' | 'cart_full'; until?: string }

const nowISO = () => new Date().toISOString()

/** Published product that exists in the shop, or null. */
export const findShopProduct = async (payload: Payload, id: number): Promise<Product | null> => {
  const { docs } = await payload.find({
    collection: 'products',
    where: { and: [{ id: { equals: id } }, { _status: { equals: 'published' } }] },
    depth: 0,
    limit: 1,
    draft: false,
  })
  return docs[0] ?? null
}

let lastSweep = 0

/**
 * Ends holds that have run out: unpaid orders past their hold become "expired"
 * and expired cart holds are deleted. Cheap enough to run on shop requests;
 * throttled per process unless forced (the scheduled task forces it).
 */
export const releaseExpired = async (payload: Payload, { force = false } = {}) => {
  const now = Date.now()
  if (!force && now - lastSweep < 20_000) return
  lastSweep = now
  const { expireOrder } = await import('./orders')
  const iso = new Date(now).toISOString()
  const { docs: orders } = await payload.find({
    collection: 'orders',
    where: { and: [{ status: { in: ['pending_payment', 'reserved'] } }, { holdExpiresAt: { less_than: iso } }] },
    depth: 0,
    limit: 100,
    pagination: false,
  })
  for (const order of orders) {
    await expireOrder(payload, order).catch((err) =>
      payload.logger.error({ err, order: order.orderNumber }, 'Could not expire order'),
    )
  }
  await payload.delete({
    collection: 'reservations',
    where: { and: [{ expiresAt: { less_than: iso } }, { order: { exists: false } }] },
  })
}

/** Holds for these products that are still running, keyed by product id. */
export const activeHoldsFor = async (payload: Payload, productIds: number[]) => {
  const map = new Map<number, Reservation>()
  if (!productIds.length) return map
  const { docs } = await payload.find({
    collection: 'reservations',
    where: { and: [{ product: { in: productIds } }, { expiresAt: { greater_than: nowISO() } }] },
    depth: 0,
    limit: productIds.length,
    pagination: false,
  })
  for (const r of docs) map.set(typeof r.product === 'object' ? r.product.id : r.product, r)
  return map
}

const cartWhere = (cartId: string): Where => ({
  and: [{ cartId: { equals: cartId } }, { expiresAt: { greater_than: nowISO() } }, { order: { exists: false } }],
})

export const getCartItems = async (payload: Payload, cartId: string | null | undefined): Promise<CartItem[]> => {
  if (!cartId) return []
  const { docs } = await payload.find({
    collection: 'reservations',
    where: cartWhere(cartId),
    depth: 2,
    sort: 'createdAt',
    limit: 20,
    pagination: false,
  })
  return docs
    .filter((r): r is Reservation & { product: Product } => typeof r.product === 'object' && r.product !== null)
    .map((r) => ({ reservationId: r.id, expiresAt: r.expiresAt, product: r.product }))
}

export const countCartItems = async (payload: Payload, cartId: string | null | undefined) => {
  if (!cartId) return 0
  const { totalDocs } = await payload.count({ collection: 'reservations', where: cartWhere(cartId) })
  return totalDocs
}

/** Hold a piece for this cart. The unique index on reservations.product settles races. */
export const holdProduct = async (
  payload: Payload,
  { productId, cartId, customerId }: { productId: number; cartId: string; customerId?: number | null },
): Promise<HoldResult> => {
  const product = await findShopProduct(payload, productId)
  if (!product) return { ok: false, reason: 'not_found' }
  if (product.status !== 'available') return { ok: false, reason: 'unavailable' }
  if (product.sellOnline === false) return { ok: false, reason: 'not_online' }

  const settings = await getShopSettings(payload)
  const { docs: existing } = await payload.find({
    collection: 'reservations',
    where: { product: { equals: productId } },
    depth: 0,
    limit: 1,
  })
  const current = existing[0]
  if (current) {
    if (new Date(current.expiresAt).getTime() > Date.now()) {
      if (current.cartId === cartId && !current.order) return { ok: true, expiresAt: current.expiresAt, alreadyHeld: true }
      return { ok: false, reason: 'held', until: current.expiresAt }
    }
    if (current.order) {
      // An unpaid order whose hold ran out: expire it properly before reuse.
      await releaseExpired(payload, { force: true })
    } else {
      await payload.delete({ collection: 'reservations', id: current.id })
    }
  }

  const inCart = await countCartItems(payload, cartId)
  if (inCart >= settings.maxItemsPerCart) return { ok: false, reason: 'cart_full' }

  const expiresAt = new Date(Date.now() + settings.cartHoldHours * 3_600_000).toISOString()
  try {
    await payload.create({
      collection: 'reservations',
      data: { product: productId, cartId, expiresAt, customer: customerId ?? undefined },
    })
  } catch (err) {
    const { docs } = await payload.find({
      collection: 'reservations',
      where: { product: { equals: productId } },
      depth: 0,
      limit: 1,
    })
    if (docs[0]) {
      if (docs[0].cartId === cartId) return { ok: true, expiresAt: docs[0].expiresAt, alreadyHeld: true }
      return { ok: false, reason: 'held', until: docs[0].expiresAt }
    }
    throw err
  }
  return { ok: true, expiresAt }
}

export const releaseFromCart = async (payload: Payload, { productId, cartId }: { productId: number; cartId: string }) => {
  await payload.delete({
    collection: 'reservations',
    where: { and: [{ product: { equals: productId } }, { cartId: { equals: cartId } }, { order: { exists: false } }] },
  })
}

/** When a guest signs in, their cart holds are linked to their account. */
export const attachCartToCustomer = async (payload: Payload, cartId: string, customerId: number) => {
  await payload.update({
    collection: 'reservations',
    where: cartWhere(cartId),
    data: { customer: customerId },
  })
}
