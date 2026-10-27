import { createHmac } from 'crypto'
import { describe, expect, it } from 'vitest'

import {
  extractRedirectParams,
  normaliseMalaysianMobile,
  signCallback,
  signRedirect,
  verifyCallback,
  verifyRedirect,
} from '@/lib/commerce/billplz'

const KEY = 'S-test-x-signature-key'

describe('Billplz X Signature', () => {
  it('builds the callback source string as Billplz documents it', () => {
    const params = { id: 'W_79pJDk', paid: 'true', amount: '200', mobile: '', x_signature: 'ignored' }
    // key+value pairs, empty values keep the key, sorted case-insensitively, joined with |
    const source = ['amount200', 'idW_79pJDk', 'mobile', 'paidtrue'].join('|')
    expect(signCallback(params, KEY)).toBe(createHmac('sha256', KEY).update(source).digest('hex'))
  })

  it('verifies a genuine callback and rejects tampering', () => {
    const params: Record<string, string> = { id: 'abc', paid: 'true', paid_amount: '150000', state: 'paid' }
    params.x_signature = signCallback(params, KEY)
    expect(verifyCallback(params, KEY)).toBe(true)
    expect(verifyCallback({ ...params, paid_amount: '1' }, KEY)).toBe(false)
    expect(verifyCallback({ ...params, x_signature: '' }, KEY)).toBe(false)
    expect(verifyCallback(params, 'wrong-key')).toBe(false)
  })

  it('prefixes redirect pairs with "billplz"', () => {
    const inner = { id: 'abc', paid: 'true', paid_at: '2026-10-27 10:00:00 +0800' }
    const source = ['billplzidabc', 'billplzpaid_at2026-10-27 10:00:00 +0800', 'billplzpaidtrue'].join('|')
    expect(signRedirect(inner, KEY)).toBe(createHmac('sha256', KEY).update(source).digest('hex'))
  })

  it('extracts and verifies redirect query parameters', () => {
    const inner = { id: 'abc', paid: 'true', paid_at: '2026-10-27' }
    const sig = signRedirect(inner, KEY)
    const qs = new URLSearchParams({
      t: 'token',
      'billplz[id]': 'abc',
      'billplz[paid]': 'true',
      'billplz[paid_at]': '2026-10-27',
      'billplz[x_signature]': sig,
    })
    const extracted = extractRedirectParams(qs)
    expect(extracted).toEqual({ ...inner, x_signature: sig })
    expect(verifyRedirect(extracted, KEY)).toBe(true)
    expect(verifyRedirect({ ...extracted, paid: 'false' }, KEY)).toBe(false)
    expect(extractRedirectParams({ 'billplz[id]': ['x', 'y'] })).toEqual({ id: 'x' })
  })
})

describe('normaliseMalaysianMobile', () => {
  it.each([
    ['+60 12-421 1205', '60124211205'],
    ['012 421 1205', '60124211205'],
    ['0111234 5678', '601112345678'],
    ['+65 9123 4567', null],
    ['03-1234 5678', null],
    ['', null],
  ])('%s → %s', (input, expected) => {
    expect(normaliseMalaysianMobile(input)).toBe(expected)
  })
})
