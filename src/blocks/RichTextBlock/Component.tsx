import type { RichTextBlock as Props } from '@/payload-types'
import { RichText } from '@/components/RichText'
import { Media } from '@/components/ui/Media'
import { ReadMore } from '@/components/ui/ReadMore'
import { extractText } from '@/lib/text'
import styles from './RichTextBlock.module.css'
import { Words } from '@/components/motion/Words'

const LONG_TEXT = 1100 // characters

export function RichTextBlock({ eyebrow, heading, content, layout, size, signature, signatureRole, portrait, anchorId, id }: Props) {
  const headingId = `text-${id}`
  // Long articles open at a comfortable height with a down arrow; short text and statements never collapse.
  const long = size !== 'statement' && extractText(content).length > LONG_TEXT
  const text = <RichText data={content} className={size === 'statement' ? styles.statement : undefined} />
  return (
    <section id={anchorId ?? undefined} className={styles.section} aria-labelledby={heading ? headingId : undefined}>
      <div className={`container ${styles.grid}`} data-layout={layout ?? 'aside'} data-letter={signature ? '' : undefined}>
        {(eyebrow || heading) && (
          <div className={styles.aside}>
            {eyebrow && <p className="eyebrow">{eyebrow}</p>}
            {heading && (
              <h2 id={headingId} className={styles.heading} data-reveal="words">
                <Words text={heading} />
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
          {long ? <ReadMore context={heading}>{text}</ReadMore> : text}
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
