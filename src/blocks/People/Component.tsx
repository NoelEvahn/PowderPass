import type { PeopleBlock as Props } from '@/payload-types'
import { ArrowLink } from '@/components/ui/ArrowLink'
import { Media } from '@/components/ui/Media'
import { Section } from '@/components/ui/Section'
import { SectionHeader } from '@/components/ui/SectionHeader'
import { resolveLink } from '@/lib/links'
import styles from './People.module.css'

export function PeopleBlock({ eyebrow, heading, intro, people, anchorId, id }: Props) {
  const headingId = `people-${id}`
  return (
    <Section id={anchorId} labelledBy={headingId}>
      <div className="container">
        <SectionHeader id={headingId} eyebrow={eyebrow} heading={heading} intro={intro} align="split" />
        <ul className={styles.grid}>
          {(people ?? []).map((person, index) => {
            const link = resolveLink(person.link)
            return (
              <li key={person.id ?? index} className={styles.person}>
                <div className={styles.photo}>
                  {person.image ? (
                    <Media media={person.image} maxSize="card" sizes="(min-width: 1024px) 25vw, 50vw" className={styles.image} />
                  ) : (
                    <span className={styles.placeholder} aria-hidden="true">
                      {person.name.split(' ').map((p) => p[0]).slice(0, 2).join('')}
                    </span>
                  )}
                </div>
                <h3 className={styles.name}>{person.name}</h3>
                <p className={styles.role}>{person.role}</p>
                {person.bio && <p className={styles.bio}>{person.bio}</p>}
                {link && (
                  <ArrowLink href={link.href} external={link.external} newTab={link.newTab}>
                    {link.label}
                  </ArrowLink>
                )}
              </li>
            )
          })}
        </ul>
      </div>
    </Section>
  )
}
