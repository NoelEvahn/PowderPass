import { CalendarDays } from 'lucide-react'
import type { SiteSetting } from '@/payload-types'
import { resolveLink } from '@/lib/links'
import { ArrowLink } from '../ui/ArrowLink'
import { DismissButton } from './AnnouncementDismiss'
import styles from './AnnouncementBar.module.css'

/** Time-limited banner from School details → Announcement. Hides itself after its expiry. */
export function AnnouncementBar({ announcement }: { announcement: SiteSetting['announcement'] }) {
  if (!announcement?.enabled || !announcement.text) return null
  if (announcement.expiresAt && new Date(announcement.expiresAt) < new Date()) return null
  const link = resolveLink(announcement.link)
  // Dismissal is remembered per announcement text, so a new announcement shows again.
  const key = `isp-announcement:${announcement.text}`
  return (
    <aside className={styles.bar} aria-label="Announcement" data-announcement={key}>
      <div className={`container ${styles.inner}`}>
        <CalendarDays size={18} aria-hidden="true" className={styles.icon} />
        <p className={styles.text}>{announcement.text}</p>
        {link && (
          <ArrowLink href={link.href} external={link.external} newTab={link.newTab} className={styles.link}>
            {link.label}
          </ArrowLink>
        )}
        <DismissButton storageKey={key} className={styles.dismiss} />
      </div>
    </aside>
  )
}
