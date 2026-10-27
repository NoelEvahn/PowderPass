import { describe, expect, it } from 'vitest'

import { checkoutSchema, enquirySchema, registerSchema, resetSchema, zodFieldErrors } from '@/lib/validation'

const base = {
  name: 'Aina',
  email: 'AINA@example.com ',
  phone: '+60 12-345 6789',
  paymentMethod: 'pay_at_shop',
  fulfilment: 'pickup',
  agree: 'on',
}

describe('checkout validation', () => {
  it('accepts a pickup order and normalises email', () => {
    const r = checkoutSchema.safeParse(base)
    expect(r.success).toBe(true)
    expect(r.success && r.data.email).toBe('aina@example.com')
  })
  it('requires an address for delivery', () => {
    const r = checkoutSchema.safeParse({ ...base, paymentMethod: 'online', fulfilment: 'delivery', postcode: '123' })
    expect(r.success).toBe(false)
    const errors = zodFieldErrors(r.error!)
    expect(Object.keys(errors).sort()).toEqual(['city', 'line1', 'postcode', 'state'])
  })
  it('requires agreeing to the terms and a usable phone', () => {
    const r = checkoutSchema.safeParse({ ...base, agree: undefined, phone: '12' })
    expect(r.success).toBe(false)
    expect(Object.keys(zodFieldErrors(r.error!)).sort()).toEqual(['agree', 'phone'])
  })
})

describe('enquiry validation', () => {
  const ok = { name: 'Mei', email: '', phone: '0123456789', topic: 'visit', message: 'Can I visit on Saturday?', consent: 'on' }
  it('accepts phone without email', () => expect(enquirySchema.safeParse(ok).success).toBe(true))
  it('needs email or phone', () => {
    const r = enquirySchema.safeParse({ ...ok, phone: '' })
    expect(r.success).toBe(false)
    expect(zodFieldErrors(r.error!).email).toMatch(/email address or a phone/)
  })
  it('rejects unknown topics', () => expect(enquirySchema.safeParse({ ...ok, topic: 'spam' }).success).toBe(false))
})

describe('account validation', () => {
  it('enforces password length', () => {
    expect(registerSchema.safeParse({ name: 'A', email: 'a@b.co', password: 'short' }).success).toBe(false)
    expect(registerSchema.safeParse({ name: 'A', email: 'a@b.co', password: 'long-enough-1' }).success).toBe(true)
  })
  it('checks reset confirmation matches', () => {
    const r = resetSchema.safeParse({ token: 'x'.repeat(20), password: 'long-enough-1', confirm: 'different-1' })
    expect(r.success).toBe(false)
  })
})
