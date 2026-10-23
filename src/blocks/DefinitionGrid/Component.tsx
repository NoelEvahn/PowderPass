import type { DefinitionGridBlock as Props } from '@/payload-types'
import { Section } from '@/components/ui/Section'
import { SectionHeader } from '@/components/ui/SectionHeader'
import styles from './DefinitionGrid.module.css'

export function DefinitionGridBlock({ eyebrow, heading, intro, items, columns, accent, background, anchorId, id }: Props) {
  const headingId = `defs-${id}`
  return (
    <Section tone={background} id={anchorId} labelledBy={headingId}>
      <div className="container">
        <SectionHeader id={headingId} eyebrow={eyebrow} heading={heading} intro={intro} align="split" />
        <dl className={styles.grid} data-columns={columns ?? '4'} data-accent={accent ?? 'violet'}>
          {(items ?? []).map((item, index) => (
            <div key={item.id ?? index} className={styles.item} data-reveal style={{ '--reveal-index': index % 4 } as React.CSSProperties}>
              <dt className={styles.term}>
                <span className={styles.number} aria-hidden="true">
                  {String(index + 1).padStart(2, '0')}
                </span>
                {item.term}
              </dt>
              {item.tagline && <dd className={styles.tagline}>{item.tagline}</dd>}
              {item.description && <dd className={styles.description}>{item.description}</dd>}
            </div>
          ))}
        </dl>
      </div>
    </Section>
  )
}
