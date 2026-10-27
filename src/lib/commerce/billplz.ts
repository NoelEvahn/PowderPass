import { createHmac, timingSafeEqual } from 'crypto'

import type { BillplzConfig } from '@/lib/env'

/**
 * Billplz v3 adapter. Billplz covers FPX online banking (Maybank2u, HSBC, PBe and
 * every other FPX bank), cards and e-wallets such as Touch 'n Go and Boost; which
 * channels appear is chosen in the Billplz collection settings.
 */

export type BillplzBill = {
  id: string
  url: string
  paid?: boolean
  state?: 'due' | 'paid' | 'deleted' | string
  paid_at?: string | null
  amount?: number
}

const authHeader = (cfg: BillplzConfig) => `Basic ${Buffer.from(`${cfg.apiKey}:`).toString('base64')}`

const request = async <T>(cfg: BillplzConfig, path: string, init: RequestInit = {}): Promise<T> => {
  const res = await fetch(`${cfg.baseURL}${path}`, {
    ...init,
    headers: { Authorization: authHeader(cfg), Accept: 'application/json', ...(init.headers ?? {}) },
    signal: AbortSignal.timeout(15_000),
    cache: 'no-store',
  })
  const text = await res.text()
  if (!res.ok) {
    throw new Error(`Billplz ${init.method ?? 'GET'} ${path} failed with ${res.status}: ${text.slice(0, 300)}`)
  }
  return (text ? JSON.parse(text) : {}) as T
}

/** Billplz accepts Malaysian mobile numbers only; anything else is left out rather than rejected. */
export const normaliseMalaysianMobile = (phone: string | null | undefined): string | null => {
  if (!phone) return null
  let digits = phone.replace(/\D/g, '')
  if (digits.startsWith('0')) digits = `6${digits}`
  return /^601\d{8,9}$/.test(digits) ? digits : null
}

export type CreateBillInput = {
  orderNumber: string
  amountSen: number
  name: string
  email: string
  phone?: string | null
  callbackURL: string
  redirectURL: string
}

export const createBill = async (cfg: BillplzConfig, input: CreateBillInput): Promise<BillplzBill> => {
  const body = new URLSearchParams({
    collection_id: cfg.collectionId,
    email: input.email,
    name: input.name.slice(0, 255),
    amount: String(input.amountSen),
    callback_url: input.callbackURL,
    redirect_url: input.redirectURL,
    description: `Label Trunc order ${input.orderNumber}`.slice(0, 200),
    reference_1_label: 'Order',
    reference_1: input.orderNumber,
  })
  const mobile = normaliseMalaysianMobile(input.phone)
  if (mobile) body.set('mobile', mobile)
  const bill = await request<BillplzBill>(cfg, '/v3/bills', {
    method: 'POST',
    body,
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
  })
  if (!bill?.id || !bill?.url) throw new Error('Billplz returned a bill without an id or url')
  return bill
}

export const getBill = (cfg: BillplzConfig, id: string) =>
  request<BillplzBill>(cfg, `/v3/bills/${encodeURIComponent(id)}`)

/** Only unpaid bills can be deleted; failures are expected for paid bills and are ignored by callers. */
export const deleteBill = (cfg: BillplzConfig, id: string) =>
  request<Record<string, never>>(cfg, `/v3/bills/${encodeURIComponent(id)}`, { method: 'DELETE' })

const hmac = (key: string, source: string) => createHmac('sha256', key).update(source).digest('hex')

const safeEqualHex = (a: string, b: string) => {
  const ab = Buffer.from(a, 'utf8')
  const bb = Buffer.from(b, 'utf8')
  return ab.length === bb.length && timingSafeEqual(ab, bb)
}

const signSources = (sources: string[], key: string) =>
  hmac(
    key,
    [...sources]
      .sort((a, b) => {
        const x = a.toLowerCase()
        const y = b.toLowerCase()
        return x < y ? -1 : x > y ? 1 : 0
      })
      .join('|'),
  )

/** X Signature for the server-to-server callback: every field except x_signature. */
export const signCallback = (params: Record<string, string>, key: string): string =>
  signSources(
    Object.entries(params)
      .filter(([k]) => k !== 'x_signature')
      .map(([k, v]) => `${k}${v ?? ''}`),
    key,
  )

export const verifyCallback = (params: Record<string, string>, key: string): boolean => {
  const given = params.x_signature
  if (!given) return false
  return safeEqualHex(signCallback(params, key), given)
}

/** Redirect parameters arrive as billplz[id], billplz[paid]… Pass the inner key/value pairs. */
export const signRedirect = (params: Record<string, string>, key: string): string =>
  signSources(
    Object.entries(params)
      .filter(([k]) => k !== 'x_signature')
      .map(([k, v]) => `billplz${k}${v ?? ''}`),
    key,
  )

export const verifyRedirect = (params: Record<string, string>, key: string): boolean => {
  const given = params.x_signature
  if (!given) return false
  return safeEqualHex(signRedirect(params, key), given)
}

/** Extract billplz[...] parameters from a redirect query string. */
export const extractRedirectParams = (search: URLSearchParams | Record<string, string | string[] | undefined>) => {
  const out: Record<string, string> = {}
  const entries =
    search instanceof URLSearchParams
      ? [...search.entries()]
      : Object.entries(search).map(([k, v]) => [k, Array.isArray(v) ? v[0] : v] as [string, string | undefined])
  for (const [k, v] of entries) {
    const m = /^billplz\[([a-z_]+)\]$/.exec(k)
    if (m && typeof v === 'string') out[m[1]] = v
  }
  return out
}
