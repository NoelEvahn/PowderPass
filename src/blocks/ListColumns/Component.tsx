import type { ListColumnsBlock as Props } from '@/payload-types'
import { Section } from '@/components/ui/Section'
import { SectionHeader } from '@/components/ui/SectionHeader'
import styles from './ListColumns.module.css'

export function ListColumnsBlock({ eyebrow, heading, intro, groups, background, anchorId, id }: Props) {
  const headingId = `lists-${id}`
  return (
    <Section tone={background} id={anchorId} labelledBy={headingId}>
      <div className="container">
        <SectionHeader id={headingId} eyebrow={eyebrow} heading={heading} intro={intro} align="split" />
        <div className={styles.groups} data-count={groups?.length ?? 0}>
          {(groups ?? []).map((group, index) => {
            const items = group.items.split('\n').map((s) => s.trim()).filter(Boolean)
            return (
              <div key={group.id ?? index} className={styles.group}>
                {group.title && <h3 className={styles.title}>{group.title}</h3>}
                <ul className={styles.list} data-long={items.length > 10 || undefined}>
                  {items.map((item) => (
                    <li key={item}>{item}</li>
                  ))}
                </ul>
                {group.note && <p className={styles.note}>{group.note}</p>}
              </div>
            )
          })}
        </div>
      </div>
    </Section>
  )
}
