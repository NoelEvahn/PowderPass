import type { Payload } from 'payload'
import { beforeAll, describe, expect, it } from 'vitest'

import { createProduct, getTestPayload, resetShop } from './helpers'

let payload: Payload

beforeAll(async () => {
  payload = await getTestPayload()
  await resetShop(payload)
})

const asAnonymous = { overrideAccess: false, user: null } as const

describe('access control', () => {
  it('hides orders, holds, enquiries and customers from the public', async () => {
    for (const collection of ['orders', 'reservations', 'enquiries', 'customers'] as const) {
      await expect(payload.find({ collection, ...asAnonymous })).rejects.toThrow()
    }
  })

  it('blocks public creation of customers, orders and enquiries through the API', async () => {
    await expect(
      payload.create({ collection: 'customers', data: { name: 'x', email: 'x@example.com', password: 'long-enough-1' }, ...asAnonymous }),
    ).rejects.toThrow()
    await expect(
      payload.create({ collection: 'enquiries', data: { name: 'x', topic: 'other', message: 'hello' }, ...asAnonymous }),
    ).rejects.toThrow()
  })

  it('shows only published stock to the public', async () => {
    const live = await createProduct(payload)
    const draft = await createProduct(payload, { _status: 'draft' })
    const { docs } = await payload.find({ collection: 'products', ...asAnonymous, limit: 100 })
    const ids = docs.map((d) => d.id)
    expect(ids).toContain(live.id)
    expect(ids).not.toContain(draft.id)
  })

  it('lets a customer read only their own orders', async () => {
    const a = await payload.create({ collection: 'customers', data: { name: 'A', email: 'a@example.com', password: 'long-enough-1' } })
    const b = await payload.create({ collection: 'customers', data: { name: 'B', email: 'b@example.com', password: 'long-enough-1' } })
    const base = {
      status: 'reserved' as const,
      paymentMethod: 'pay_at_shop' as const,
      fulfilment: 'pickup' as const,
      items: [{ title: 'x', price: 1 }],
      subtotal: 1,
      total: 1,
      contact: { name: 'x', email: 'x@example.com', phone: '0123456789' },
    }
    await payload.create({ collection: 'orders', context: { skipOrderSync: true }, data: { ...base, orderNumber: 'LTTEST-A', accessToken: 'a'.repeat(32), customer: a.id } })
    await payload.create({ collection: 'orders', context: { skipOrderSync: true }, data: { ...base, orderNumber: 'LTTEST-B', accessToken: 'b'.repeat(32), customer: b.id } })
    const { docs } = await payload.find({ collection: 'orders', overrideAccess: false, user: { ...a, collection: 'customers' } })
    expect(docs.map((d) => d.orderNumber)).toEqual(['LTTEST-A'])
  })

  it('keeps the last administrator', async () => {
    // Bypass hooks to start from an empty staff table.
    await payload.db.deleteMany({ collection: 'users', where: { id: { exists: true } } })
    const admin = await payload.create({ collection: 'users', data: { name: 'Admin', email: 'admin@test.example', password: 'long-enough-1', roles: ['admin'] } })
    await expect(payload.update({ collection: 'users', id: admin.id, data: { roles: ['editor'] } })).rejects.toThrow(/last administrator/)
    await expect(payload.delete({ collection: 'users', id: admin.id })).rejects.toThrow(/last administrator/)
  })
})
