import { ChevronDown } from 'lucide-react'
import type { HeroBlock as HeroProps } from '@/payload-types'
import { CmsLinks } from '@/components/ui/CmsLink'
import { Media } from '@/components/ui/Media'
import { SpatialExplorer } from './SpatialExplorer'
import styles from './Hero.module.css'

/**
 * Heading split into lines. Only heroes further down a page animate in: the first
 * hero is above the fold and must paint immediately (it is usually the LCP element).
 */
const HeadingLines = ({ text, reveal }: { text: string; reveal: boolean }) => (
  <>
    {text
      .split('\n')
      .filter(Boolean)
      .map((line, index) => (
        <span key={index} className={styles.line} data-reveal={reveal || undefined} style={{ '--reveal-index': index } as React.CSSProperties}>
          {line}
        </span>
      ))}
  </>
)

export function HeroBlock(props: HeroProps & { isFirst?: boolean }) {
  const { variant, eyebrow, heading, intro, links, factLine, media, slides, accent, stageLabel, isFirst } = props

  if (variant === 'home') {
    const explorerSlides = (slides ?? []).flatMap((slide) =>
      slide.image && typeof slide.image === 'object' ? [{ id: slide.id ?? String(slide.image.id), image: slide.image, caption: slide.caption }] : [],
    )
    return (
      <section className={styles.home} aria-labelledby="page-title">
        <div className={`container ${styles.homeGrid}`}>
          <div className={styles.homeText}>
            {eyebrow && <p className="eyebrow">{eyebrow}</p>}
            <h1 id="page-title" className={styles.homeTitle}>
              <HeadingLines text={heading} reveal={!isFirst} />
            </h1>
            {intro && <p className={styles.homeIntro}>{intro}</p>}
            <CmsLinks links={links} className={styles.actions} />
            {factLine && <p className={styles.factLine}>{factLine}</p>}
          </div>
          {explorerSlides.length > 0 && (
            <div className={styles.homeMedia}>
              <SpatialExplorer slides={explorerSlides} />
            </div>
          )}
        </div>
        <div className={`container ${styles.cueRow}`}>
          <a href="#after-hero" className={styles.scrollCue}>
            <span className={styles.cueArrow} aria-hidden="true">
              <ChevronDown size={22} />
            </span>
            <span>Scroll to discover</span>
          </a>
        </div>
        <span id="after-hero" className={styles.afterHero} tabIndex={-1} />
      </section>
    )
  }

  if (variant === 'compact') {
    return (
      <section className={styles.compact} aria-labelledby="page-title">
        <div className="container">
          {eyebrow && <p className="eyebrow">{eyebrow}</p>}
          <h1 id="page-title" className={styles.pageTitle} data-drift>
            <HeadingLines text={heading} reveal={!isFirst} />
          </h1>
          {intro && <p className={`lead ${styles.pageIntro}`}>{intro}</p>}
          <CmsLinks links={links} className={styles.actions} />
        </div>
      </section>
    )
  }

  const isStage = variant === 'stage'
  return (
    <section className={`${styles.page} ${isStage ? styles.stage : ''}`} data-accent={accent ?? 'violet'} aria-labelledby="page-title">
      <div className={`container ${styles.pageGrid}`}>
        <div className={styles.pageText}>
          {isStage && stageLabel && <p className={styles.stageLabel}>{stageLabel}</p>}
          {!isStage && eyebrow && <p className="eyebrow">{eyebrow}</p>}
          {isStage && eyebrow && <p className="eyebrow">{eyebrow}</p>}
          <h1 id="page-title" className={styles.pageTitle} data-drift>
            <HeadingLines text={heading} reveal={!isFirst} />
          </h1>
          {intro && <p className={`lead ${styles.pageIntro}`}>{intro}</p>}
          <CmsLinks links={links} className={styles.actions} />
          {factLine && <p className={styles.factLine}>{factLine}</p>}
        </div>
        {media && (
          <figure className={styles.pageMedia} data-reveal={isFirst ? undefined : 'media'}>
            <span data-parallax="48">
              <Media
                media={media}
                priority={isFirst}
                maxSize="hero"
                sizes="(min-width: 1024px) 58vw, 100vw"
                className={styles.pageImage}
              />
            </span>
          </figure>
        )}
      </div>
    </section>
  )
}
