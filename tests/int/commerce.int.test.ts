import { randomUUID } from 'crypto'
import type { Payload } from 'payload'
import { afterAll, beforeAll, beforeEach, describe, expect, it } from 'vitest'

import { getCartItems, holdProduct, releaseExpired, releaseFromCart } from '@/lib/commerce/cart'
import { cancelUnpaidOrder, checkout, confirmOnlinePayment, findOrderForViewer } from '@/lib/commerce/orders'

import { checkoutInput, createProduct, getTestPayload, resetShop, startMockBillplz, stopBillplzEnv, type MockBillplz } from './helpers'

let payload: Payload

beforeAll(async () => {
  payload = await getTestPayload()
})

beforeEach(async () => {
  stopBillplzEnv()
  await resetShop(payload)
})

const product = (id: number) => payload.findByID({ collection: 'products', id, depth: 0 })

describe('holds', () => {
  it('holds a piece for one cart only', async () => {
    const p = await createProduct(payload)
    const a = randomUUID()
    const b = randomUUID()
    const first = await holdProduct(payload, { productId: p.id, cartId: a })
    expect(first.ok).toBe(true)
    if (first.ok) {
      const hours = (new Date(first.expiresAt).getTime() - Date.now()) / 3_600_000
      expect(hours).toBeGreaterThan(23.9)
      expect(hours).toBeLessThanOrEqual(24)
    }
    expect(await holdProduct(payload, { productId: p.id, cartId: a })).toMatchObject({ ok: true, alreadyHeld: true })
    expect(await holdProduct(payload, { productId: p.id, cartId: b })).toMatchObject({ ok: false, reason: 'held' })
    await releaseFromCart(payload, { productId: p.id, cartId: a })
    expect(await holdProduct(payload, { productId: p.id, cartId: b })).toMatchObject({ ok: true })
  })

  it('lets exactly one of many simultaneous carts win', async () => {
    const p = await createProduct(payload)
    const results = await Promise.all(
      Array.from({ length: 12 }, () => holdProduct(payload, { productId: p.id, cartId: randomUUID() }).catch((e) => ({ ok: false, error: e }))),
    )
    expect(results.filter((r) => r.ok)).toHaveLength(1)
    const { totalDocs } = await payload.count({ collection: 'reservations', where: { product: { equals: p.id } } })
    expect(totalDocs).toBe(1)
  })

  it('refuses sold, unpublished, boutique-only pieces and full carts', async () => {
    const sold = await createProduct(payload, { status: 'sold' })
    const boutique = await createProduct(payload, { sellOnline: false })
    const draft = await createProduct(payload, { _status: 'draft' })
    const cart = randomUUID()
    expect(await holdProduct(payload, { productId: sold.id, cartId: cart })).toMatchObject({ reason: 'unavailable' })
    expect(await holdProduct(payload, { productId: boutique.id, cartId: cart })).toMatchObject({ reason: 'not_online' })
    expect(await holdProduct(payload, { productId: draft.id, cartId: cart })).toMatchObject({ reason: 'not_found' })

    await payload.updateGlobal({ slug: 'shop-settings', data: { maxItemsPerCart: 1 } })
    const p1 = await createProduct(payload)
    const p2 = await createProduct(payload)
    expect((await holdProduct(payload, { productId: p1.id, cartId: cart })).ok).toBe(true)
    expect(await holdProduct(payload, { productId: p2.id, cartId: cart })).toMatchObject({ reason: 'cart_full' })
  })

  it('releases expired cart holds so others can buy', async () => {
    const p = await createProduct(payload)
    const a = randomUUID()
    await holdProduct(payload, { productId: p.id, cartId: a })
    await payload.update({
      collection: 'reservations',
      where: { product: { equals: p.id } },
      data: { expiresAt: new Date(Date.now() - 1000).toISOString() },
    })
    expect(await getCartItems(payload, a)).toHaveLength(0)
    expect(await holdProduct(payload, { productId: p.id, cartId: randomUUID() })).toMatchObject({ ok: true })
  })
})

describe('reserve and pay at the boutique', () => {
  it('creates a reserved order that holds the piece, and cancelling releases it', async () => {
    const p = await createProduct(payload, { price: 2500 })
    const cart = randomUUID()
    await holdProduct(payload, { productId: p.id, cartId: cart })
    const result = await checkout(payload, { cartId: cart, input: checkoutInput() })
    expect(result.ok).toBe(true)
    if (!result.ok) return
    expect(result.order.status).toBe('reserved')
    expect(result.order.total).toBe(2500)
    expect(result.redirectTo).toContain(`/order/${result.order.orderNumber}?t=`)
    expect(await getCartItems(payload, cart)).toHaveLength(0)
    expect(await holdProduct(payload, { productId: p.id, cartId: randomUUID() })).toMatchObject({ reason: 'held' })

    // The order page needs the secret token.
    expect(await findOrderForViewer(payload, { orderNumber: result.order.orderNumber, token: 'wrong' })).toBeNull()
    expect(await findOrderForViewer(payload, { orderNumber: result.order.orderNumber, token: result.order.accessToken })).not.toBeNull()

    const cancelled = await cancelUnpaidOrder(payload, result.order)
    expect(cancelled.status).toBe('cancelled')
    expect(await holdProduct(payload, { productId: p.id, cartId: randomUUID() })).toMatchObject({ ok: true })
  })

  it('expires the order when the hold runs out', async () => {
    const p = await createProduct(payload)
    const cart = randomUUID()
    await holdProduct(payload, { productId: p.id, cartId: cart })
    const result = await checkout(payload, { cartId: cart, input: checkoutInput() })
    if (!result.ok) throw new Error(result.error)
    const past = new Date(Date.now() - 60_000).toISOString()
    await payload.update({ collection: 'orders', id: result.order.id, data: { holdExpiresAt: past }, context: { skipOrderSync: true } })
    await payload.update({ collection: 'reservations', where: { order: { equals: result.order.id } }, data: { expiresAt: past } })
    await releaseExpired(payload, { force: true })
    expect((await payload.findByID({ collection: 'orders', id: result.order.id })).status).toBe('expired')
    expect(await holdProduct(payload, { productId: p.id, cartId: randomUUID() })).toMatchObject({ ok: true })
  })

  it('staff marking the order paid marks the piece sold', async () => {
    const p = await createProduct(payload)
    const cart = randomUUID()
    await holdProduct(payload, { productId: p.id, cartId: cart })
    const result = await checkout(payload, { cartId: cart, input: checkoutInput() })
    if (!result.ok) throw new Error(result.error)
    await payload.update({ collection: 'orders', id: result.order.id, data: { status: 'paid' } })
    expect((await product(p.id)).status).toBe('sold')
    const { totalDocs } = await payload.count({ collection: 'reservations', where: { product: { equals: p.id } } })
    expect(totalDocs).toBe(0)
    const order = await payload.findByID({ collection: 'orders', id: result.order.id })
    expect(order.payment?.paidAt).toBeTruthy()

    // Publishing an older draft cannot quietly un-sell a piece with a paid order.
    await payload.update({ collection: 'products', id: p.id, draft: false, data: { status: 'available' } })
    expect((await product(p.id)).status).toBe('sold')

    // Cancelling the paid order returns the piece to the shop.
    await payload.update({ collection: 'orders', id: result.order.id, data: { status: 'cancelled' } })
    expect((await product(p.id)).status).toBe('available')
  })

  it('refuses checkout when the method is switched off', async () => {
    await payload.updateGlobal({ slug: 'shop-settings', data: { payAtShopEnabled: false } })
    const p = await createProduct(payload)
    const cart = randomUUID()
    await holdProduct(payload, { productId: p.id, cartId: cart })
    expect(await checkout(payload, { cartId: cart, input: checkoutInput() })).toMatchObject({ ok: false, code: 'method' })
    expect(await checkout(payload, { cartId: cart, input: checkoutInput({ paymentMethod: 'online' }) })).toMatchObject({
      ok: false,
      code: 'method',
    })
  })
})

describe('online payment (Billplz)', () => {
  let billplz: MockBillplz
  beforeEach(async () => {
    billplz = await startMockBillplz()
  })
  afterAll(() => stopBillplzEnv())

  const onlineCheckout = async (price = 1200) => {
    const p = await createProduct(payload, { price })
    const cart = randomUUID()
    await holdProduct(payload, { productId: p.id, cartId: cart })
    const result = await checkout(payload, { cartId: cart, input: checkoutInput({ paymentMethod: 'online' }) })
    if (!result.ok) throw new Error(result.error)
    return { p, cart, result }
  }

  it('creates a bill for the total in sen and redirects to it', async () => {
    const { result } = await onlineCheckout(15999.5)
    expect(result.order.status).toBe('pending_payment')
    expect(result.redirectTo).toMatch(/^https:\/\/billplz\.test\/bills\//)
    expect(billplz.bills.get(result.order.payment!.billId!)!.amount).toBe(1599950)
    const hold = await payload.find({ collection: 'reservations', where: { order: { equals: result.order.id } } })
    expect(hold.docs).toHaveLength(1)
  })

  it('confirms payment once, marks the piece sold, and ignores duplicates', async () => {
    const { p, result } = await onlineCheckout()
    const billId = result.order.payment!.billId!
    const [a, b] = await Promise.all([
      confirmOnlinePayment(payload, { billId, amountSen: 120000, transactionId: 'T1' }),
      confirmOnlinePayment(payload, { billId, amountSen: 120000, transactionId: 'T1' }),
    ])
    expect(a?.status).toBe('paid')
    expect(b?.status).toBe('paid')
    expect((await product(p.id)).status).toBe('sold')
    expect((await payload.findByID({ collection: 'orders', id: result.order.id })).payment?.transactionId).toBe('T1')
  })

  it('flags a refund when the amount does not match', async () => {
    const { p, result } = await onlineCheckout()
    const order = await confirmOnlinePayment(payload, { billId: result.order.payment!.billId!, amountSen: 100 })
    expect(order?.status).toBe('refund_due')
    expect((await product(p.id)).status).toBe('available')
  })

  it('keeps a late payment if the piece is still free, otherwise flags a refund', async () => {
    const first = await onlineCheckout()
    await payload.update({ collection: 'orders', id: first.result.order.id, data: { status: 'expired' }, context: { skipOrderSync: true } })
    await payload.delete({ collection: 'reservations', where: { order: { equals: first.result.order.id } } })
    expect((await confirmOnlinePayment(payload, { billId: first.result.order.payment!.billId! }))?.status).toBe('paid')

    const second = await onlineCheckout()
    await payload.update({ collection: 'orders', id: second.result.order.id, data: { status: 'expired' }, context: { skipOrderSync: true } })
    await payload.delete({ collection: 'reservations', where: { order: { equals: second.result.order.id } } })
    await holdProduct(payload, { productId: second.p.id, cartId: randomUUID() })
    expect((await confirmOnlinePayment(payload, { billId: second.result.order.payment!.billId! }))?.status).toBe('refund_due')
  })

  it('puts pieces back in the cart if Billplz is down', async () => {
    billplz.fail = true
    const p = await createProduct(payload)
    const cart = randomUUID()
    await holdProduct(payload, { productId: p.id, cartId: cart })
    const result = await checkout(payload, { cartId: cart, input: checkoutInput({ paymentMethod: 'online' }) })
    expect(result).toMatchObject({ ok: false, code: 'payment_provider' })
    expect(await getCartItems(payload, cart)).toHaveLength(1)
  })

  it('cancelling an unpaid online order deletes the bill', async () => {
    const { result } = await onlineCheckout()
    await cancelUnpaidOrder(payload, result.order)
    expect(billplz.bills.get(result.order.payment!.billId!)!.deleted).toBe(true)
  })

  afterAll(async () => {
    await billplz?.close()
  })
})
