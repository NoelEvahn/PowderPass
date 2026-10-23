import type { RichTextBlock as Props } from '@/payload-types'
import { RichText } from '@/components/RichText'
import { Media } from '@/components/ui/Media'
import styles from './RichTextBlock.module.css'

export function RichTextBlock({ eyebrow, heading, content, layout, size, signature, signatureRole, portrait, anchorId, id }: Props) {
  const headingId = `text-${id}`
  return (
    <section id={anchorId ?? undefined} className={styles.section} aria-labelledby={heading ? headingId : undefined}>
      <div className={`container ${styles.grid}`} data-layout={layout ?? 'aside'}>
        {(eyebrow || heading) && (
          <div className={styles.aside}>
            {eyebrow && <p className="eyebrow">{eyebrow}</p>}
            {heading && (
              <h2 id={headingId} className={styles.heading} data-reveal>
                {heading}
              </h2>
            )}
            {signature && portrait && (
              <figure className={styles.portrait}>
                <Media media={portrait} maxSize="card" sizes="240px" className={styles.portraitImage} />
              </figure>
            )}
          </div>
        )}
        <div className={styles.body}>
          <RichText data={content} className={size === 'statement' ? styles.statement : undefined} />
          {signature && (
            <p className={styles.signature}>
              <span className={styles.name}>{signature}</span>
              {signatureRole && <span className={styles.role}>{signatureRole}</span>}
            </p>
          )}
        </div>
      </div>
    </section>
  )
}
