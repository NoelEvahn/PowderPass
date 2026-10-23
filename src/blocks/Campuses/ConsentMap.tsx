'use client'
import { MapPinned } from 'lucide-react'
import { useConsent } from '@/components/consent/ConsentProvider'
import styles from './Campuses.module.css'

/** Google Maps embed that only loads after the visitor allows third-party media. */
export function ConsentMap({ query, title }: { query: string; title: string }) {
  const { consent, grant } = useConsent()
  if (consent?.media) {
    return (
      <iframe
        className={styles.map}
        title={title}
        src={`https://www.google.com/maps?q=${encodeURIComponent(query)}&output=embed`}
        loading="lazy"
        referrerPolicy="no-referrer-when-downgrade"
      />
    )
  }
  return (
    <div className={styles.mapPlaceholder}>
      <MapPinned size={28} aria-hidden="true" />
      <p>The embedded map is provided by Google, which may set cookies.</p>
      <button type="button" className={styles.mapButton} onClick={() => grant('media')}>
        Allow maps and load
      </button>
    </div>
  )
}
