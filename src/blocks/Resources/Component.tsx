import { CalendarDays, ExternalLink, FileText, Mail, MonitorSmartphone } from 'lucide-react'
import type { Media as MediaDoc, ResourcesBlock as Props } from '@/payload-types'
import { Section } from '@/components/ui/Section'
import { SectionHeader } from '@/components/ui/SectionHeader'
import { isExternalHref } from '@/lib/links'
import styles from './Resources.module.css'

const ICONS = { document: FileText, website: ExternalLink, calendar: CalendarDays, portal: MonitorSmartphone, email: Mail }

export function ResourcesBlock({ eyebrow, heading, intro, items, anchorId, id }: Props) {
  const headingId = `resources-${id}`
  return (
    <Section id={anchorId} labelledBy={headingId} spacing="tight">
      <div className="container">
        <SectionHeader id={headingId} eyebrow={eyebrow} heading={heading} intro={intro} />
        <ul className={styles.list}>
          {(items ?? []).map((item, index) => {
            const file = item.file && typeof item.file === 'object' ? (item.file as MediaDoc) : null
            const href = file?.url ?? item.url
            const external = isExternalHref(href)
            const Icon = ICONS[item.kind ?? 'document']
            return (
              <li key={item.id ?? index}>
                <a
                  href={href}
                  className={styles.item}
                  {...(external || file ? { target: '_blank', rel: 'noopener noreferrer' } : {})}
                >
                  <span className={styles.icon} aria-hidden="true">
                    <Icon size={20} />
                  </span>
                  <span className={styles.text}>
                    <span className={styles.title}>{item.title}</span>
                    {item.description && <span className={styles.description}>{item.description}</span>}
                  </span>
                  {(external || file) && (
                    <span className={styles.external}>
                      <ExternalLink size={16} aria-hidden="true" />
                      <span className="visually-hidden">(opens in a new tab)</span>
                    </span>
                  )}
                </a>
              </li>
            )
          })}
        </ul>
      </div>
    </Section>
  )
}
