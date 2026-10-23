import { CalendarDays, Clock, MapPin, Users } from 'lucide-react'
import type { EventsListBlock as Props } from '@/payload-types'
import { Button } from '@/components/ui/Button'
import { Section } from '@/components/ui/Section'
import { SectionHeader } from '@/components/ui/SectionHeader'
import { formatDay, formatMonthShort, formatTimeRange, formatWeekday } from '@/lib/format'
import { getUpcomingEvents } from '@/lib/queries'
import { jsonLdString, siteUrl } from '@/lib/seo'
import styles from './EventsList.module.css'

const STATUS = { open: 'Registration open', waitlist: 'Waiting list', full: 'Fully booked', none: 'No registration needed' } as const

export async function EventsListBlock({ eyebrow, heading, intro, campus, limit, emptyText, anchorId, id }: Props) {
  const headingId = `events-${id}`
  const events = await getUpcomingEvents({ campus: campus ?? 'all', limit: limit ?? 4 })

  const jsonLd = events.map((event) => ({
    '@context': 'https://schema.org',
    '@type': 'Event',
    name: event.title,
    startDate: event.startsAt,
    endDate: event.endsAt,
    eventAttendanceMode: 'https://schema.org/OfflineEventAttendanceMode',
    eventStatus: 'https://schema.org/EventScheduled',
    description: event.summary,
    location: { '@type': 'Place', name: event.location ?? 'The International School @ ParkCity', address: 'Desa ParkCity, 52200 Kuala Lumpur, Malaysia' },
    organizer: { '@type': 'School', name: 'The International School @ ParkCity', url: siteUrl() },
    ...(event.registrationUrl ? { url: event.registrationUrl } : {}),
  }))

  return (
    <Section id={anchorId} labelledBy={headingId} tone="tint">
      <div className="container">
        <SectionHeader id={headingId} eyebrow={eyebrow} heading={heading} intro={intro} align="split" />
        {events.length === 0 ? (
          <p className={styles.empty}>{emptyText}</p>
        ) : (
          <>
            <script type="application/ld+json" dangerouslySetInnerHTML={{ __html: jsonLdString(jsonLd) }} />
            <ul className={styles.list}>
              {events.map((event) => {
                const status = (event.registrationStatus ?? 'open') as keyof typeof STATUS
                const canRegister = event.registrationUrl && (status === 'open' || status === 'waitlist')
                return (
                  <li key={event.id} className={styles.event}>
                    <div className={styles.date} aria-hidden="true">
                      <span className={styles.day}>{formatDay(event.startsAt)}</span>
                      <span className={styles.month}>{formatMonthShort(event.startsAt)}</span>
                    </div>
                    <div className={styles.body}>
                      <p className={styles.status} data-status={status}>
                        {STATUS[status]}
                      </p>
                      <h3 className={styles.title}>{event.title}</h3>
                      <ul className={styles.facts}>
                        <li>
                          <CalendarDays size={18} aria-hidden="true" />
                          <time dateTime={event.startsAt}>{formatWeekday(event.startsAt)}</time>
                        </li>
                        <li>
                          <Clock size={18} aria-hidden="true" />
                          {formatTimeRange(event.startsAt, event.endsAt)}
                        </li>
                        {event.location && (
                          <li>
                            <MapPin size={18} aria-hidden="true" />
                            {event.location}
                          </li>
                        )}
                        {event.audience && (
                          <li>
                            <Users size={18} aria-hidden="true" />
                            {event.audience}
                          </li>
                        )}
                      </ul>
                      <p className={styles.summary}>{event.summary}</p>
                    </div>
                    {canRegister && (
                      <div className={styles.action}>
                        <Button href={event.registrationUrl!} variant="primary" newTab>
                          Register
                          <span className="visually-hidden"> for {event.title}</span>
                        </Button>
                      </div>
                    )}
                  </li>
                )
              })}
            </ul>
          </>
        )}
      </div>
    </Section>
  )
}
