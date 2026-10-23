'use client'
import { createContext, type ReactNode, useCallback, useContext, useMemo, useState, useSyncExternalStore } from 'react'
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

/*
 * The consent cookie is treated as an external store. Reading it in the browser
 * (rather than on the server) keeps every page fully cacheable; the server
 * snapshot is "unknown", so nothing optional loads until the browser has read it.
 */
const listeners = new Set<() => void>()
const UNKNOWN = '\u0000'
const readCookie = () => document.cookie.split('; ').find((c) => c.startsWith(`${CONSENT_COOKIE}=`))?.slice(CONSENT_COOKIE.length + 1) ?? ''
const subscribe = (listener: () => void) => {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

function writeConsent(state: ConsentState) {
  const secure = window.location.protocol === 'https:' ? '; Secure' : ''
  document.cookie = `${CONSENT_COOKIE}=${serializeConsent(state)}; Max-Age=${CONSENT_MAX_AGE_DAYS * 86400}; Path=/; SameSite=Lax${secure}`
  listeners.forEach((listener) => listener())
}

export function ConsentProvider({ children }: { children: ReactNode }) {
  const raw = useSyncExternalStore(subscribe, readCookie, () => UNKNOWN)
  const consent = useMemo(() => (raw === UNKNOWN ? undefined : parseConsent(raw)), [raw])
  const [preferencesOpen, setPreferencesOpen] = useState(false)

  const decide = useCallback((choices: Pick<ConsentState, 'analytics' | 'media'>) => {
    const withdrewAnalytics = Boolean(parseConsent(readCookie())?.analytics && !choices.analytics)
    writeConsent(makeConsent(choices))
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
