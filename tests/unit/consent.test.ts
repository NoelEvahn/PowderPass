import { describe, expect, it } from 'vitest'
import { CONSENT_VERSION, makeConsent, parseConsent, serializeConsent } from '@/components/consent/consent'

describe('cookie consent', () => {
  it('round-trips a stored choice', () => {
    const stored = serializeConsent(makeConsent({ analytics: true, media: false }))
    expect(parseConsent(stored)).toMatchObject({ version: CONSENT_VERSION, analytics: true, media: false })
  })

  it('treats missing, malformed or outdated values as "no choice made"', () => {
    expect(parseConsent(undefined)).toBeNull()
    expect(parseConsent('%7Bbroken')).toBeNull()
    expect(parseConsent(encodeURIComponent(JSON.stringify({ version: 0, analytics: true })))).toBeNull()
  })

  it('never turns a non-boolean into consent', () => {
    const raw = encodeURIComponent(JSON.stringify({ version: CONSENT_VERSION, analytics: 'yes', media: 1 }))
    expect(parseConsent(raw)).toMatchObject({ analytics: false, media: false })
  })
})
