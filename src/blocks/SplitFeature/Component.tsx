import type { SplitFeatureBlock as Props } from '@/payload-types'
import { RichText } from '@/components/RichText'
import { CmsLinks } from '@/components/ui/CmsLink'
import { Media } from '@/components/ui/Media'
import { Section } from '@/components/ui/Section'
import styles from './SplitFeature.module.css'
import { Words } from '@/components/motion/Words'

export function SplitFeatureBlock({ eyebrow, heading, body, media, mediaPosition, background, links, anchorId, id }: Props) {
  const headingId = `split-${id}`
  return (
    <Section tone={background} id={anchorId} labelledBy={headingId}>
      <div className={`container ${styles.grid}`} data-media={mediaPosition ?? 'right'}>
        <figure className={styles.media} data-reveal="media">
          <span data-parallax>
            <Media media={media} maxSize="feature" sizes="(min-width: 1024px) 50vw, 100vw" className={styles.image} />
          </span>
        </figure>
        <div className={styles.text}>
          {eyebrow && <p className="eyebrow">{eyebrow}</p>}
          <h2 id={headingId} className={styles.heading} data-reveal="words">
            <Words text={heading} />
          </h2>
          <RichText data={body} />
          <CmsLinks links={links} className={styles.actions} />
        </div>
      </div>
    </Section>
  )
}
