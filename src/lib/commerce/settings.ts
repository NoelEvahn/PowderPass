import type { Payload } from 'payload'

import { getBillplzConfig } from '@/lib/env'
import type { ShopSetting, SiteSetting } from '@/payload-types'

export type ResolvedShopSettings = {
  cartHoldHours: number
  payAtShopHoldHours: number
  paymentWindowMinutes: number
  maxItemsPerCart: number
  onlinePayment: boolean
  payAtShop: boolean
  delivery: boolean
  deliveryFee: number
  deliveryNote: string
  showSoldDays: number
}

const clamp = (n: unknown, min: number, max: number, fallback: number) => {
  const v = typeof n === 'number' && Number.isFinite(n) ? n : fallback
  return Math.min(max, Math.max(min, v))
}

/** Shop settings with safe defaults and limits, and payment options only where they can work. */
export const resolveShopSettings = (raw: Partial<ShopSetting> | null | undefined): ResolvedShopSettings => {
  const onlinePayment = raw?.onlinePaymentEnabled !== false && getBillplzConfig() !== null
  return {
    cartHoldHours: clamp(raw?.cartHoldHours, 1, 24, 24),
    payAtShopHoldHours: clamp(raw?.payAtShopHoldHours, 1, 168, 24),
    paymentWindowMinutes: clamp(raw?.paymentWindowMinutes, 10, 120, 30),
    maxItemsPerCart: clamp(raw?.maxItemsPerCart, 1, 10, 3),
    onlinePayment,
    payAtShop: raw?.payAtShopEnabled !== false,
    // Delivery needs online payment, because the fee is paid upfront.
    delivery: Boolean(raw?.deliveryEnabled) && onlinePayment,
    deliveryFee: clamp(raw?.deliveryFee, 0, 100_000, 0),
    deliveryNote: raw?.deliveryNote ?? '',
    showSoldDays: clamp(raw?.showSoldDays, 0, 365, 30),
  }
}

export const getShopSettings = async (payload: Payload): Promise<ResolvedShopSettings> => {
  const raw = await payload.findGlobal({ slug: 'shop-settings', depth: 0 }).catch(() => null)
  return resolveShopSettings(raw)
}

export const getSiteSettings = async (payload: Payload): Promise<SiteSetting> =>
  payload.findGlobal({ slug: 'site-settings', depth: 0 })
