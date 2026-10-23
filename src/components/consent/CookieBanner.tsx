'use client'
import { Cookie } from 'lucide-react'
import Link from 'next/link'
import { useState, useSyncExternalStore } from 'react'
import { Dialog } from '../ui/Dialog'
import { CATEGORY_INFO, type ConsentCategory } from './consent'
import styles from './CookieBanner.module.css'
import { useConsent } from './ConsentProvider'

/**
 * First-visit banner with equal-weight Accept / Reject / Customise choices,
 * plus the preferences dialog (also opened from the footer "Cookie settings").
 * The banner is a non-modal region so the page stays usable before choosing.
 */
const noSubscribe = () => () => {}

/** Rendered only while the dialog is open, so it always starts from the visitor's current choice. */
function PreferencesForm() {
  const { consent, decide } = useConsent()
  const [choices, setChoices] = useState({ analytics: consent?.analytics ?? false, media: consent?.media ?? false })
  return (
    <form
      className={styles.form}
      onSubmit={(event) => {
        event.preventDefault()
        decide(choices)
      }}
    >
      <p className={styles.intro}>Choose which optional cookies we may use. You can change this at any time from “Cookie settings” in the footer.</p>
      <fieldset className={styles.fieldset}>
        <legend className="visually-hidden">Cookie categories</legend>
        <div className={styles.option}>
          <div>
            <p className={styles.optionTitle}>Strictly necessary</p>
            <p className={styles.optionText}>Remember your cookie choice and keep staff signed in to the editor. Always on.</p>
          </div>
          <span className={styles.always}>Always on</span>
        </div>
        {(Object.keys(CATEGORY_INFO) as ConsentCategory[]).map((key) => (
          <label key={key} className={styles.option}>
            <span>
              <span className={styles.optionTitle}>{CATEGORY_INFO[key].title}</span>
              <span className={styles.optionText}>{CATEGORY_INFO[key].description}</span>
            </span>
            <input
              type="checkbox"
              role="switch"
              className={styles.switch}
              checked={choices[key]}
              onChange={(event) => setChoices((c) => ({ ...c, [key]: event.target.checked }))}
            />
          </label>
        ))}
      </fieldset>
      <div className={styles.formActions}>
        <button type="submit" className={styles.primary}>
          Save choices
        </button>
        <button type="button" className={styles.secondary} onClick={() => decide({ analytics: true, media: true })}>
          Accept all
        </button>
        <button type="button" className={styles.secondary} onClick={() => decide({ analytics: false, media: false })}>
          Reject optional
        </button>
      </div>
    </form>
  )
}

export function CookieBanner() {
  const { consent, decide, preferencesOpen, openPreferences, closePreferences } = useConsent()
  // Inside the CMS live-preview frame the banner would only get in the editor's way.
  const inFrame = useSyncExternalStore(noSubscribe, () => window.self !== window.top, () => false)

  return (
    <>
      {consent === null && !inFrame && (
        <section className={styles.banner} aria-labelledby="cookie-banner-title">
          <div className={styles.text}>
            <h2 id="cookie-banner-title" className={styles.title}>
              <Cookie size={20} aria-hidden="true" /> Cookies on this website
            </h2>
            <p>
              We use essential cookies to make the site work. With your permission we would also use analytics and embedded maps.
              Nothing optional runs until you choose. <Link href="/cookies">Cookie policy</Link>
            </p>
          </div>
          <div className={styles.actions}>
            <button type="button" className={styles.primary} onClick={() => decide({ analytics: true, media: true })}>
              Accept all
            </button>
            <button type="button" className={styles.primary} onClick={() => decide({ analytics: false, media: false })}>
              Reject optional
            </button>
            <button type="button" className={styles.secondary} onClick={openPreferences}>
              Customise
            </button>
          </div>
        </section>
      )}

      <Dialog open={preferencesOpen} onClose={closePreferences} title="Cookie settings" variant="panel">
        <PreferencesForm />
      </Dialog>
    </>
  )
}
