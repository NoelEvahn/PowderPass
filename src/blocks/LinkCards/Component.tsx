import { ArrowRight } from 'lucide-react'
import type { LinkCardsBlock as Props } from '@/payload-types'
import { Media } from '@/components/ui/Media'
import { Section } from '@/components/ui/Section'
import { SectionHeader } from '@/components/ui/SectionHeader'
import { SmartLink } from '@/components/ui/SmartLink'
import { resolveLink } from '@/lib/links'
import styles from './LinkCards.module.css'

/** Whole-card links (no nested controls). Hover and keyboard focus share the same emphasis. */
export function LinkCardsBlock({ eyebrow, heading, intro, style, cards, anchorId, id }: Props) {
  const headingId = `cards-${id}`
  const isList = style === 'list'
  return (
    <Section id={anchorId} labelledBy={headingId}>
      <div className="container">
        <SectionHeader id={headingId} eyebrow={eyebrow} heading={heading} intro={intro} align="split" />
        <ul className={isList ? styles.list : styles.grid} data-count={cards?.length ?? 0}>
          {(cards ?? []).map((card, index) => {
            const link = resolveLink(card.link)
            if (!link) return null
            return (
              <li key={card.id ?? index} data-reveal style={{ '--reveal-index': index % 4 } as React.CSSProperties}>
                <SmartLink href={link.href} newTab={link.newTab} className={isList ? styles.row : styles.card} data-accent={card.accent ?? 'violet'}>
                  {!isList && card.media && (
                    <span className={styles.media}>
                      <Media media={card.media} maxSize="card" sizes="(min-width: 1024px) 30vw, 100vw" className={styles.image} />
                    </span>
                  )}
                  <span className={styles.body}>
                    {card.meta && <span className={styles.meta}>{card.meta}</span>}
                    <span className={styles.title}>{card.title}</span>
                    {card.text && <span className={styles.text}>{card.text}</span>}
                    <span className={styles.cta}>
                      {link.label}
                      <ArrowRight size={18} aria-hidden="true" className={styles.arrow} />
                    </span>
                  </span>
                </SmartLink>
              </li>
            )
          })}
        </ul>
      </div>
    </Section>
  )
}
