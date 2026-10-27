import config from '@payload-config'
import http from 'http'
import type { AddressInfo } from 'net'
import { getPayload, type Payload } from 'payload'
import sharp from 'sharp'

let payloadPromise: Promise<Payload> | null = null
export const getTestPayload = () => (payloadPromise ??= getPayload({ config }))

/** Removes shop data between tests (keeps globals). */
export const resetShop = async (payload: Payload) => {
  for (const collection of ['reservations', 'orders', 'enquiries', 'products', 'brands', 'categories', 'customers'] as const) {
    await payload.delete({ collection, where: { id: { exists: true } }, overrideAccess: true })
  }
  await payload.updateGlobal({
    slug: 'shop-settings',
    data: {
      cartHoldHours: 24,
      payAtShopHoldHours: 24,
      paymentWindowMinutes: 30,
      maxItemsPerCart: 3,
      onlinePaymentEnabled: true,
      payAtShopEnabled: true,
      deliveryEnabled: false,
      showSoldDays: 30,
    },
  })
  await payload.updateGlobal({
    slug: 'site-settings',
    data: {
      businessName: 'Label Trunc',
      phone: '+60 12-421 1205',
      whatsapp: '60124211205',
      email: 'shop@example.com',
      address: { line1: 'Test address', postcode: '68000', city: 'Ampang', state: 'Selangor' },
    },
  })
}

let mediaId: number | null = null
const getMediaId = async (payload: Payload) => {
  if (mediaId) {
    const existing = await payload.find({ collection: 'media', where: { id: { equals: mediaId } }, limit: 1 })
    if (existing.docs.length) return mediaId
  }
  const data = await sharp({ create: { width: 40, height: 50, channels: 3, background: '#cccccc' } }).png().toBuffer()
  const media = await payload.create({
    collection: 'media',
    data: { alt: 'Test image used only by automated tests' },
    file: { data, mimetype: 'image/png', name: 'int-test.png', size: data.length },
  })
  mediaId = media.id
  return media.id
}

export const createProduct = async (payload: Payload, overrides: Record<string, unknown> = {}) => {
  const brand =
    (await payload.find({ collection: 'brands', where: { name: { equals: 'Test Brand' } }, limit: 1 })).docs[0] ??
    (await payload.create({ collection: 'brands', data: { name: 'Test Brand' } }))
  return payload.create({
    collection: 'products',
    draft: false,
    data: {
      title: `Test piece ${Math.random().toString(36).slice(2, 8)}`,
      brand: brand.id,
      price: 1000,
      condition: 'excellent',
      gallery: [await getMediaId(payload)],
      status: 'available',
      _status: 'published',
      ...overrides,
    } as never,
  })
}

export type MockBillplz = { url: string; bills: Map<string, { amount: number; deleted?: boolean }>; close: () => Promise<void>; fail: boolean }

/** A local stand-in for the Billplz API, so checkout can be tested without real credentials. */
export const startMockBillplz = async (): Promise<MockBillplz> => {
  const bills = new Map<string, { amount: number; deleted?: boolean }>()
  const state: { fail: boolean } = { fail: false }
  const server = http.createServer((req, res) => {
    let body = ''
    req.on('data', (c) => (body += c))
    req.on('end', () => {
      if (state.fail) {
        res.writeHead(500).end('{"error":"down"}')
        return
      }
      if (req.method === 'POST' && req.url === '/v3/bills') {
        const params = new URLSearchParams(body)
        const id = `bill_${bills.size + 1}_${Date.now()}`
        bills.set(id, { amount: Number(params.get('amount')) })
        res.writeHead(200, { 'Content-Type': 'application/json' })
        res.end(JSON.stringify({ id, url: `https://billplz.test/bills/${id}`, amount: Number(params.get('amount')) }))
        return
      }
      const m = /^\/v3\/bills\/(.+)$/.exec(req.url ?? '')
      if (req.method === 'DELETE' && m) {
        const bill = bills.get(decodeURIComponent(m[1]))
        if (bill) bill.deleted = true
        res.writeHead(200, { 'Content-Type': 'application/json' }).end('{}')
        return
      }
      res.writeHead(404).end('{}')
    })
  })
  await new Promise<void>((r) => server.listen(0, '127.0.0.1', r))
  const { port } = server.address() as AddressInfo
  const mock = {
    url: `http://127.0.0.1:${port}`,
    bills,
    get fail() {
      return state.fail
    },
    set fail(v: boolean) {
      state.fail = v
    },
    close: () => new Promise<void>((r) => server.close(() => r())),
  }
  process.env.BILLPLZ_API_KEY = 'test-key'
  process.env.BILLPLZ_COLLECTION_ID = 'test-collection'
  process.env.BILLPLZ_X_SIGNATURE_KEY = 'test-x-signature'
  process.env.BILLPLZ_API_URL = mock.url
  return mock
}

export const stopBillplzEnv = () => {
  delete process.env.BILLPLZ_API_KEY
  delete process.env.BILLPLZ_COLLECTION_ID
  delete process.env.BILLPLZ_X_SIGNATURE_KEY
  delete process.env.BILLPLZ_API_URL
}

export const checkoutInput = (overrides: Record<string, unknown> = {}) => ({
  name: 'Test Customer',
  email: 'customer@example.com',
  phone: '+60 12-345 6789',
  paymentMethod: 'pay_at_shop' as const,
  fulfilment: 'pickup' as const,
  ...overrides,
})
