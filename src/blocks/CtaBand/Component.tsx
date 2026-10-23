import type { CtaBandBlock as Props } from '@/payload-types'
import { CmsLinks } from '@/components/ui/CmsLink'
import { Media } from '@/components/ui/Media'
import styles from './CtaBand.module.css'

export function CtaBandBlock({ eyebrow, heading, text, media, links, anchorId, id }: Props) {
  const headingId = `cta-${id}`
  return (
    <section id={anchorId ?? undefined} className={styles.section} aria-labelledby={headingId}>
      <div className="container">
        <div className={styles.band} data-tone="ink" data-has-media={media ? '' : undefined}>
          <div className={styles.text}>
            {eyebrow && <p className="eyebrow">{eyebrow}</p>}
            <h2 id={headingId} className={styles.heading} data-reveal>
              {heading}
            </h2>
            {text && <p className={styles.body}>{text}</p>}
            <CmsLinks links={links} className={styles.actions} />
          </div>
          {media && (
            <div className={styles.media}>
              <Media media={media} maxSize="feature" sizes="(min-width: 1024px) 40vw, 100vw" className={styles.image} />
            </div>
          )}
        </div>
      </div>
    </section>
  )
}
