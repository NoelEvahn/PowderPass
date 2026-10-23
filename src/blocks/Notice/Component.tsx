import { AlertTriangle, Clock, Info } from 'lucide-react'
import type { NoticeBlock as Props } from '@/payload-types'
import { ArrowLink } from '@/components/ui/ArrowLink'
import { resolveLink } from '@/lib/links'
import styles from './Notice.module.css'

const ICONS = { info: Info, important: AlertTriangle, pending: Clock }

export function NoticeBlock({ tone, title, body, link, anchorId }: Props) {
  const Icon = ICONS[tone ?? 'info']
  const resolved = resolveLink(link)
  return (
    <div id={anchorId ?? undefined} className={`container ${styles.wrap}`}>
      <aside className={styles.notice} data-tone={tone ?? 'info'} aria-label={tone === 'pending' ? 'Awaiting school verification' : 'Notice'}>
        <Icon size={22} aria-hidden="true" className={styles.icon} />
        <div className={styles.text}>
          <p className={styles.title}>{title}</p>
          {body && <p className={styles.body}>{body}</p>}
          {resolved && (
            <ArrowLink href={resolved.href} external={resolved.external} newTab={resolved.newTab}>
              {resolved.label}
            </ArrowLink>
          )}
        </div>
      </aside>
    </div>
  )
}
