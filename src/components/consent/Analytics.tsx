'use client'
import Script from 'next/script'
import { usePathname } from 'next/navigation'
import { useEffect } from 'react'
import { useConsent } from './ConsentProvider'

declare global {
  interface Window {
    dataLayer?: unknown[]
    gtag?: (...args: unknown[]) => void
  }
}

/** Loads Google Analytics 4 only after the visitor has accepted analytics cookies. */
export function Analytics({ measurementId }: { measurementId?: string }) {
  const { consent } = useConsent()
  const pathname = usePathname()
  // Only accept a well-formed GA4 ID, since it is written into an inline script.
  const validId = measurementId && /^G-[A-Z0-9]{4,16}$/.test(measurementId) ? measurementId : null
  const enabled = Boolean(validId && consent?.analytics)

  useEffect(() => {
    if (enabled && window.gtag) window.gtag('event', 'page_view', { page_path: pathname })
  }, [enabled, pathname])

  if (!enabled) return null
  return (
    <>
      <Script src={`https://www.googletagmanager.com/gtag/js?id=${validId}`} strategy="afterInteractive" />
      <Script id="ga-init" strategy="afterInteractive">
        {`window.dataLayer=window.dataLayer||[];function gtag(){dataLayer.push(arguments)}window.gtag=gtag;gtag('js',new Date());gtag('config','${validId}',{anonymize_ip:true,send_page_view:false});`}
      </Script>
    </>
  )
}

/** Anonymous interaction events (no personal data). Silently does nothing without consent. */
export const trackEvent = (name: string, params: Record<string, string | number> = {}) => {
  if (typeof window !== 'undefined' && window.gtag) window.gtag('event', name, params)
}
