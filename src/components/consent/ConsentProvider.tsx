'use client'
import { createContext, type ReactNode, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react'
import {
  CONSENT_COOKIE,
  CONSENT_MAX_AGE_DAYS,
  type ConsentState,
  makeConsent,
  parseConsent,
  serializeConsent,
} from './consent'

type ConsentContextValue = {
  /** undefined until the stored choice has been read in the browser; null when no choice has been made. */
  consent: ConsentState | null | undefined
  decide: (choices: Pick<ConsentState, 'analytics' | 'media'>) => void
  grant: (category: 'analytics' | 'media') => void
  preferencesOpen: boolean
  openPreferences: () => void
  closePreferences: () => void
}

const ConsentContext = createContext<ConsentContextValue | null>(null)

const ANALYTICS_COOKIE = /^(_ga|_gid|_gat)/

/** Remove analytics cookies when consent is withdrawn (on this host and its parent domain). */
function clearAnalyticsCookies() {
  const host = window.location.hostname
  const domains = ['', host, `.${host.split('.').slice(-2).join('.')}`]
  document.cookie.split(';').forEach((pair) => {
    const name = pair.split('=')[0]?.trim()
    if (name && ANALYTICS_COOKIE.test(name)) {
      domains.forEach((domain) => {
        document.cookie = `${name}=; Max-Age=0; path=/${domain ? `; domain=${domain}` : ''}`
      })
    }
  })
}

const readCookie = () => {
  const match = document.cookie.split('; ').find((c) => c.startsWith(`${CONSENT_COOKIE}=`))
  return parseConsent(match?.slice(CONSENT_COOKIE.length + 1))
}

/**
 * The choice is read in the browser (not on the server) so pages stay fully
 * cacheable. Nothing optional loads while the state is still unknown.
 */
export function ConsentProvider({ children }: { children: ReactNode }) {
  const [consent, setConsent] = useState<ConsentState | null | undefined>(undefined)
  const [preferencesOpen, setPreferencesOpen] = useState(false)
  const consentRef = useRef(consent)

  useEffect(() => {
    consentRef.current = consent
  }, [consent])

  useEffect(() => {
    setConsent(readCookie())
  }, [])

  const decide = useCallback((choices: Pick<ConsentState, 'analytics' | 'media'>) => {
    const next = makeConsent(choices)
    const secure = window.location.protocol === 'https:' ? '; Secure' : ''
    document.cookie = `${CONSENT_COOKIE}=${serializeConsent(next)}; Max-Age=${CONSENT_MAX_AGE_DAYS * 86400}; Path=/; SameSite=Lax${secure}`
    const withdrewAnalytics = Boolean(consentRef.current?.analytics && !next.analytics)
    setConsent(next)
    setPreferencesOpen(false)
    if (withdrewAnalytics) {
      clearAnalyticsCookies()
      // A loaded analytics script cannot be unloaded from memory; a reload removes it.
      window.location.reload()
    }
  }, [])

  const grant = useCallback(
    (category: 'analytics' | 'media') =>
      decide({ analytics: consent?.analytics ?? false, media: consent?.media ?? false, [category]: true }),
    [consent, decide],
  )

  const value = useMemo(
    () => ({
      consent,
      decide,
      grant,
      preferencesOpen,
      openPreferences: () => setPreferencesOpen(true),
      closePreferences: () => setPreferencesOpen(false),
    }),
    [consent, decide, grant, preferencesOpen],
  )

  return <ConsentContext.Provider value={value}>{children}</ConsentContext.Provider>
}

export function useConsent() {
  const ctx = useContext(ConsentContext)
  if (!ctx) throw new Error('useConsent must be used inside ConsentProvider')
  return ctx
}
