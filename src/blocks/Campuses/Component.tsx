import { Mail, MapPin, Phone } from 'lucide-react'
import type { CampusesBlock as Props } from '@/payload-types'
import { ArrowLink } from '@/components/ui/ArrowLink'
import { CmsLinks } from '@/components/ui/CmsLink'
import { Media } from '@/components/ui/Media'
import { Section } from '@/components/ui/Section'
import { SectionHeader } from '@/components/ui/SectionHeader'
import { getSettings } from '@/lib/queries'
import { formatDate } from '@/lib/format'
import { ConsentMap } from './ConsentMap'
import styles from './Campuses.module.css'

const telHref = (number: string) => `tel:${number.replace(/[^\d+]/g, '')}`

export async function CampusesBlock({ eyebrow, heading, intro, showMaps, media, links, anchorId, id }: Props) {
  const headingId = `campuses-${id}`
  const settings = await getSettings()
  const campuses = settings.campuses ?? []
  return (
    <Section id={anchorId} labelledBy={headingId}>
      <div className="container">
        <SectionHeader id={headingId} eyebrow={eyebrow} heading={heading} intro={intro} align="split">
          <CmsLinks links={links} className={styles.actions} />
        </SectionHeader>
        {media && (
          <figure className={styles.hero} data-reveal="media">
            <Media media={media} maxSize="hero" sizes="100vw" className={styles.heroImage} />
          </figure>
        )}
        <ul className={styles.grid}>
          {campuses.map((campus) => (
            <li key={campus.id ?? campus.name} className={styles.campus}>
              {campus.image && (
                <div className={styles.media}>
                  <Media media={campus.image} maxSize="card" sizes="(min-width: 1024px) 45vw, 100vw" className={styles.image} />
                </div>
              )}
              <div className={styles.body}>
                <h3 className={styles.name}>{campus.name}</h3>
                {campus.ages && <p className={styles.ages}>{campus.ages}</p>}
                <address className={styles.address}>
                  <MapPin size={20} aria-hidden="true" />
                  <span>{campus.address}</span>
                </address>
                <ul className={styles.contacts}>
                  {(campus.phones ?? []).map((phone) => (
                    <li key={phone.number}>
                      <Phone size={18} aria-hidden="true" />
                      <span className={styles.phoneLabel}>{phone.label}</span>
                      <a href={telHref(phone.number)}>{phone.number}</a>
                      {phone.callsOnly && <span className={styles.note}>(calls only)</span>}
                    </li>
                  ))}
                  {campus.email && (
                    <li>
                      <Mail size={18} aria-hidden="true" />
                      <span className={styles.phoneLabel}>Email</span>
                      <a href={`mailto:${campus.email}`}>{campus.email}</a>
                    </li>
                  )}
                </ul>
                {campus.directions && <p className={styles.directions}>{campus.directions}</p>}
                {campus.mapUrl && (
                  <ArrowLink href={campus.mapUrl} external newTab>
                    Open in Google Maps
                  </ArrowLink>
                )}
                {showMaps && campus.mapEmbedQuery && <ConsentMap query={campus.mapEmbedQuery} title={`Map of ${campus.name}`} />}
                {campus.lastVerified && <p className={styles.verified}>Details checked {formatDate(campus.lastVerified)}</p>}
              </div>
            </li>
          ))}
        </ul>
      </div>
    </Section>
  )
}
