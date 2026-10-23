import type { FeatureMosaicBlock as Props } from '@/payload-types'
import { Media } from '@/components/ui/Media'
import { Section } from '@/components/ui/Section'
import { SectionHeader } from '@/components/ui/SectionHeader'
import { SmartLink } from '@/components/ui/SmartLink'
import { resolveLink } from '@/lib/links'
import { ArrowRight } from 'lucide-react'
import styles from './FeatureMosaic.module.css'

/** Asymmetric grid of large and tall images with concise captions. Each item is a single link. */
export function FeatureMosaicBlock({ eyebrow, heading, intro, items, anchorId, id }: Props) {
  const headingId = `mosaic-${id}`
  return (
    <Section id={anchorId} labelledBy={headingId}>
      <div className="container">
        <SectionHeader id={headingId} eyebrow={eyebrow} heading={heading} intro={intro} align="split" />
        <ul className={styles.grid} data-count={items?.length ?? 0}>
          {(items ?? []).map((item, index) => {
            const link = resolveLink(item.link)
            const content = (
              <>
                <div className={styles.media}>
                  <Media media={item.media} maxSize="feature" sizes="(min-width: 1024px) 45vw, 100vw" className={styles.image} />
                </div>
                <div className={styles.caption}>
                  {item.category && <p className={styles.category}>{item.category}</p>}
                  <h3 className={styles.title}>
                    {item.title}
                    {link && <ArrowRight size={22} aria-hidden="true" className={styles.arrow} />}
                  </h3>
                  {item.text && <p className={styles.text}>{item.text}</p>}
                </div>
              </>
            )
            return (
              <li key={item.id ?? index} className={styles.item} data-reveal style={{ '--reveal-index': index % 2 } as React.CSSProperties}>
                {link ? (
                  <SmartLink href={link.href} newTab={link.newTab} className={styles.card}>
                    {content}
                  </SmartLink>
                ) : (
                  <div className={styles.card}>{content}</div>
                )}
              </li>
            )
          })}
        </ul>
      </div>
    </Section>
  )
}
