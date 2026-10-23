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

/**
 * Calm, scroll-lit statement. The section holds still while the visitor scrolls
 * (about 1.6 extra viewport heights, so it never feels rushed); each word in turn
 * brightens from the muted ink colour to white and a gold underline draws in
 * beneath it. Nothing moves sideways. The decorative "@" turns a few degrees.
 * Unlit words keep the muted ink colour (about 10:1 on violet), so every word is
 * always readable. Reduced motion: all words lit, no pinning.
 */
export function KineticWords({ headingId, anchorId, eyebrow, words, supporting, link }: Props) {
  const reduced = useReducedMotion()
  const outerRef = useRef<HTMLDivElement>(null)
  const wordRefs = useRef<(HTMLSpanElement | null)[]>([])
  const atRef = useRef<HTMLSpanElement>(null)

  useEffect(() => {
    const spans = wordRefs.current
    if (reduced) {
      spans.forEach((el) => el?.style.removeProperty('--lit'))
      if (atRef.current) atRef.current.style.transform = ''
      return
    }
    const count = Math.max(1, words.length)
    return subscribeScroll(() => {
      const outer = outerRef.current
      if (!outer) return
      const rect = outer.getBoundingClientRect()
      const travel = rect.height - window.innerHeight
      const p = travel > 0 ? Math.min(1, Math.max(0, -rect.top / travel)) : 1
      // Words light one after another between 10% and 80% of the travel.
      const span = 0.7 / count
      spans.forEach((el, i) => {
        if (!el) return
        const start = 0.1 + i * span
        el.style.setProperty('--lit', easeInOut(segment(p, start, start + span * 0.9)).toFixed(3))
      })
      if (atRef.current) atRef.current.style.transform = `translate(-50%, -50%) rotate(${(p * 12 - 6).toFixed(2)}deg)`
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
