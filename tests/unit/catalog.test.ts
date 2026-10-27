import { describe, expect, it } from 'vitest'

import { CONDITION_OPTIONS, formatRM, labelFor, toSen } from '@/lib/catalog'
import { slugify } from '@/fields/slug'
import { generateOrderNumber } from '@/lib/commerce/orders'

describe('money', () => {
  it('formats ringgit', () => {
    expect(formatRM(12500)).toBe('RM 12,500')
    expect(formatRM(99.5)).toBe('RM 99.50')
    expect(formatRM(null)).toBe('RM 0')
  })
  it('converts to sen without float drift', () => {
    expect(toSen(0.1 + 0.2)).toBe(30)
    expect(toSen(15999.99)).toBe(1599999)
  })
})

describe('labels and slugs', () => {
  it('finds option labels', () => {
    expect(labelFor(CONDITION_OPTIONS, 'very-good')).toBe('Pre-loved: very good')
    expect(labelFor(CONDITION_OPTIONS, 'nope')).toBe('')
  })
  it('slugifies names with accents and symbols', () => {
    expect(slugify('Hermès Kelly 25 & Strap')).toBe('hermes-kelly-25-and-strap')
    expect(slugify('  --Classic   Flap!!  ')).toBe('classic-flap')
  })
})

describe('order numbers', () => {
  it('uses the Malaysian date and an unambiguous suffix', () => {
    const n = generateOrderNumber(new Date('2026-10-27T17:30:00Z')) // 01:30 on 28 Oct in KL
    expect(n).toMatch(/^LT261028-[A-HJ-NP-Z2-9]{5}$/)
  })
})
