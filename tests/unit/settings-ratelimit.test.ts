import { afterEach, describe, expect, it } from 'vitest'

import { resolveShopSettings } from '@/lib/commerce/settings'
import { rateLimit, resetRateLimits } from '@/lib/rate-limit'

const withBillplz = () => {
  process.env.BILLPLZ_API_KEY = 'k'
  process.env.BILLPLZ_COLLECTION_ID = 'c'
  process.env.BILLPLZ_X_SIGNATURE_KEY = 'x'
}

afterEach(() => {
  delete process.env.BILLPLZ_API_KEY
  delete process.env.BILLPLZ_COLLECTION_ID
  delete process.env.BILLPLZ_X_SIGNATURE_KEY
  resetRateLimits()
})

describe('shop settings', () => {
  it('caps the cart hold at 24 hours and applies defaults', () => {
    const s = resolveShopSettings({ cartHoldHours: 72 } as never)
    expect(s.cartHoldHours).toBe(24)
    expect(s.maxItemsPerCart).toBe(3)
    expect(s.payAtShop).toBe(true)
  })
  it('only offers online payment and delivery when Billplz is configured', () => {
    expect(resolveShopSettings({ onlinePaymentEnabled: true, deliveryEnabled: true } as never)).toMatchObject({
      onlinePayment: false,
      delivery: false,
    })
    withBillplz()
    expect(resolveShopSettings({ onlinePaymentEnabled: true, deliveryEnabled: true } as never)).toMatchObject({
      onlinePayment: true,
      delivery: true,
    })
    expect(resolveShopSettings({ onlinePaymentEnabled: false, deliveryEnabled: true } as never)).toMatchObject({
      onlinePayment: false,
      delivery: false,
    })
  })
})

describe('rate limiting', () => {
  it('blocks after the limit within the window', () => {
    for (let i = 0; i < 3; i++) expect(rateLimit('k', 3, 60_000).ok).toBe(true)
    const blocked = rateLimit('k', 3, 60_000)
    expect(blocked.ok).toBe(false)
    expect(blocked.retryAfterMs).toBeGreaterThan(0)
    expect(rateLimit('other', 3, 60_000).ok).toBe(true)
  })
})
