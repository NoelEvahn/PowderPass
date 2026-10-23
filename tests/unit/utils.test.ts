import { describe, expect, it } from 'vitest'
import { slugify } from '@/fields/slug'
import { rateLimit, resetRateLimits } from '@/lib/rate-limit'
import { clientIp } from '@/lib/request'
import { excerptAround, extractText } from '@/lib/text'
import { legacyRedirects } from '@/lib/redirects.mjs'

describe('slugify', () => {
  it('produces clean, URL-safe slugs', () => {
    expect(slugify('Fees & Scholarships 2026/27')).toBe('fees-and-scholarships-2026-27')
    expect(slugify('  Café   Menu ')).toBe('cafe-menu')
  })
})

describe('rate limiter', () => {
  it('blocks after the limit and resets after the window', () => {
    resetRateLimits()
    const opts = { limit: 2, windowMs: 1000 }
    expect(rateLimit('k', opts, 0).ok).toBe(true)
    expect(rateLimit('k', opts, 10).ok).toBe(true)
    expect(rateLimit('k', opts, 20).ok).toBe(false)
    expect(rateLimit('k', opts, 1001).ok).toBe(true)
  })
})

describe('clientIp', () => {
  it('ignores the spoofable first X-Forwarded-For entry', () => {
    expect(clientIp(new Headers({ 'x-forwarded-for': '6.6.6.6, 10.0.0.2' }))).toBe('10.0.0.2')
    expect(clientIp(new Headers({ 'x-real-ip': '1.2.3.4', 'x-forwarded-for': '6.6.6.6' }))).toBe('1.2.3.4')
  })
})

describe('search text extraction', () => {
  it('collects readable text from blocks and rich text, skipping URLs and ids', () => {
    const text = extractText([
      { blockType: 'hero', id: 'abc', heading: 'Sixth Form', links: [{ link: { url: 'https://x.test', label: 'Apply' } }] },
      { content: { root: { children: [{ children: [{ text: 'A-Level subjects' }] }] } } },
    ])
    expect(text).toContain('Sixth Form')
    expect(text).toContain('A-Level subjects')
    expect(text).not.toContain('https://x.test')
    expect(text).not.toContain('abc')
  })

  it('builds an excerpt around the match', () => {
    expect(excerptAround('a'.repeat(200) + ' school bus ' + 'b'.repeat(200), 'bus', 20)).toMatch(/^….*bus.*…$/)
  })
})

describe('legacy redirects', () => {
  it('maps old WordPress URLs permanently and never to the homepage', () => {
    expect(legacyRedirects.find((r) => r.source === '/about-us/welcome')).toEqual({ source: '/about-us/welcome', destination: '/discover/leadership', permanent: true })
    expect(legacyRedirects.every((r) => r.destination !== '/')).toBe(true)
  })
})
