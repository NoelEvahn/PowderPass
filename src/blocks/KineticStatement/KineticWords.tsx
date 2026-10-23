'use client'
import { useEffect, useRef } from 'react'
import { ArrowLink } from '@/components/ui/ArrowLink'
import type { ResolvedLink } from '@/lib/links'
import { easeInOut, segment, subscribeScroll } from '@/components/motion/scroll'
import { useReducedMotion } from '@/components/motion/useReducedMotion'
import styles from './Kinetic.module.css'

type Props = {
  headingId: string
  anchorId?: string
  eyebrow?: string | null
  words: string[]
  supporting?: string | null
  link: ResolvedLink | null
}

/** How far the words close up in the stacked phase (1 = fully overlapping). */
const BUNCH = 0.6
/** Lowest opacity of the outer words; keeps white-on-violet contrast above 3:1 for large text. */
const MIN_OPACITY = 0.62

/** Settled indents (vw) — the composition shown at rest and with reduced motion. */
const SETTLED = [-7, 6, -1, 4]

/**
 * Scroll choreography over the section's travel (progress p from 0 to 1):
 * stack 0–.15 → separate .15–.5 → move outward .5–.75 (≤ 20vw) → settle by .9.
 * The moving spans are the real heading text, so it is read once, in order.
 */
export function KineticWords({ headingId, anchorId, eyebrow, words, supporting, link }: Props) {
  const reduced = useReducedMotion()
  const outerRef = useRef<HTMLDivElement>(null)
  const wordRefs = useRef<(HTMLSpanElement | null)[]>([])
  const atRef = useRef<HTMLSpanElement>(null)

  useEffect(() => {
    const spans = wordRefs.current
    if (reduced) {
      spans.forEach((el) => el && (el.style.cssText = ''))
      if (atRef.current) atRef.current.style.cssText = ''
      return
    }
    const mid = (words.length - 1) / 2
    return subscribeScroll(() => {
      const outer = outerRef.current
      if (!outer) return
      const rect = outer.getBoundingClientRect()
      const travel = rect.height - window.innerHeight
      const p = travel > 0 ? Math.min(1, Math.max(0, -rect.top / travel)) : 1
      const sep = easeInOut(segment(p, 0.15, 0.5))
      const out = easeInOut(segment(p, 0.5, 0.75))
      const settle = easeInOut(segment(p, 0.75, 0.9))
      const maxSide = window.innerWidth < 768 ? 12 : 20

      spans.forEach((el, i) => {
        if (!el) return
        const lineHeight = el.offsetHeight
        // Bunched rather than fully overlapping, so every word stays legible.
        const y = (1 - sep) * (mid - i) * lineHeight * BUNCH
        const outward = (i % 2 === 0 ? -1 : 1) * maxSide * out
        const x = outward + ((SETTLED[i] ?? 0) - outward) * settle
        const opacity = i === Math.round(mid) ? 1 : MIN_OPACITY + (1 - MIN_OPACITY) * sep
        el.style.transform = `translate3d(${x.toFixed(2)}vw, ${y.toFixed(1)}px, 0)`
        el.style.opacity = opacity.toFixed(3)
        el.style.letterSpacing = `${(-0.07 + 0.04 * sep).toFixed(4)}em`
      })
      if (atRef.current) atRef.current.style.transform = `translate(-50%, -50%) rotate(${(p * 36 - 18).toFixed(2)}deg) scale(${(0.92 + p * 0.16).toFixed(3)})`
    })
  }, [reduced, words.length])

  return (
    <section id={anchorId} className={styles.section} data-tone="ink" aria-labelledby={headingId} data-animated={!reduced || undefined}>
      <div ref={outerRef} className={styles.outer}>
        <div className={styles.sticky}>
          <span ref={atRef} className={styles.at} aria-hidden="true">
            @
          </span>
          <div className={`container ${styles.inner}`}>
            {eyebrow && <p className={`eyebrow ${styles.eyebrow}`}>{eyebrow}</p>}
            <h2 id={headingId} className={styles.words}>
              {words.map((word, i) => (
                <span
                  key={word + i}
                  ref={(el) => {
                    wordRefs.current[i] = el
                  }}
                  className={styles.word}
                  style={{ '--settled': `${SETTLED[i] ?? 0}vw` } as React.CSSProperties}
                >
                  {word}
                  {i < words.length - 1 ? ' ' : ''}
                </span>
              ))}
            </h2>
            {(supporting || link) && (
              <div className={styles.footer}>
                {supporting && <p className={styles.supporting}>{supporting}</p>}
                {link && (
                  <ArrowLink href={link.href} external={link.external} newTab={link.newTab}>
                    {link.label}
                  </ArrowLink>
                )}
              </div>
            )}
          </div>
        </div>
      </div>
    </section>
  )
}
