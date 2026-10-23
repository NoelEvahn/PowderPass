/**
 * Cookie consent model. Necessary cookies are always on; every other category
 * is off until the visitor opts in. The choice is stored in a first-party
 * cookie so it is available on the first server render too.
 */
export const CONSENT_COOKIE = 'isp_consent'
export const CONSENT_VERSION = 1
export const CONSENT_MAX_AGE_DAYS = 180

export type ConsentCategory = 'analytics' | 'media'
export type ConsentState = { version: number; analytics: boolean; media: boolean; decidedAt: string }

export const CATEGORY_INFO: Record<ConsentCategory, { title: string; description: string }> = {
  analytics: {
    title: 'Analytics',
    description:
      'Anonymous statistics (Google Analytics) about which pages are useful, so we can improve the website. No form content or personal details are sent.',
  },
  media: {
    title: 'Maps and third-party media',
    description: 'Embedded Google Maps on the contact and visit pages. Google may set its own cookies when a map loads.',
  },
}

export function parseConsent(raw: string | undefined | null): ConsentState | null {
  if (!raw) return null
  try {
    const value = JSON.parse(decodeURIComponent(raw)) as Partial<ConsentState>
    if (value.version !== CONSENT_VERSION) return null
    return {
      version: CONSENT_VERSION,
      analytics: value.analytics === true,
      media: value.media === true,
      decidedAt: typeof value.decidedAt === 'string' ? value.decidedAt : new Date(0).toISOString(),
    }
  } catch {
    return null
  }
}

export const serializeConsent = (state: ConsentState) => encodeURIComponent(JSON.stringify(state))

export const makeConsent = (choices: Pick<ConsentState, 'analytics' | 'media'>): ConsentState => ({
  version: CONSENT_VERSION,
  analytics: choices.analytics,
  media: choices.media,
  decidedAt: new Date().toISOString(),
})
