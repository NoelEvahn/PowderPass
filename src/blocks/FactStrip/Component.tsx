import type { FactStripBlock as Props } from '@/payload-types'
import styles from './FactStrip.module.css'

/** Verified facts only (no counters or animated statistics). */
export function FactStripBlock({ heading, facts, anchorId, id }: Props) {
  const headingId = `facts-${id}`
  return (
    <section id={anchorId ?? undefined} className={styles.section} aria-labelledby={heading ? headingId : undefined} aria-label={heading ? undefined : 'Key facts'}>
      <div className="container">
        {heading && (
          <h2 id={headingId} className={styles.heading}>
            {heading}
          </h2>
        )}
        <dl className={styles.list}>
          {(facts ?? []).map((fact, index) => (
            <div key={fact.id ?? index} className={styles.fact} data-reveal style={{ '--reveal-index': index } as React.CSSProperties}>
              <dt className={styles.label}>{fact.label}</dt>
              <dd className={styles.value}>{fact.value}</dd>
            </div>
          ))}
        </dl>
      </div>
    </section>
  )
}
