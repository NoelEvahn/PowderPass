'use client'
import { useEffect, useRef, useState } from 'react'
import { damp, helixStyle } from '@/components/motion/helix'
import { clamp, subscribeScroll } from '@/components/motion/scroll'
import { useMediaQuery, useReducedMotion } from '@/components/motion/useReducedMotion'
import { iconFor } from '@/lib/icons'
import styles from './TermSpiral.module.css'

export type SpiralTerm = { id: string; term: string; tagline?: string | null; icon?: string | null }

type Props = {
  headingId: string
  header: React.ReactNode
  items: SpiralTerm[]
  accent: string
}

const STEP_VH = 0.5 // viewport heights of scroll per term

/**
 * Terms on a scroll-driven helix: the section pins and vertical scrolling turns
 * the spiral one term at a time, then releases. On small or short screens and
 * with reduced motion the same cards become a native sideways swipe row.
 */
export function TermSpiral({ headingId, header, items, accent }: Props) {
  const reduced = useReducedMotion()
  const largeEnough = useMediaQuery('(min-width: 900px) and (min-height: 640px)')
  const pinned = largeEnough && !reduced && items.length > 1
  const outerRef = useRef<HTMLDivElement>(null)
  const stageRef = useRef<HTMLOListElement>(null)
  const barRef = useRef<HTMLSpanElement>(null)
  const cardRefs = useRef<(HTMLLIElement | null)[]>([])
  const [active, setActive] = useState(0)
  const count = items.length
  const afterId = `${headingId}-after`

  useEffect(() => {
    const stage = stageRef.current
    if (!pinned || !stage) {
      cardRefs.current.forEach((card) => card?.removeAttribute('style'))
      return
    }
    const m = { target: 0, current: 0, frame: 0, last: 0, active: -1 }
    const layout = () => {
      const w = stage.clientWidth
      const options = { radius: w * 0.75, step: 30, rise: -22, visible: 3.2 }
      cardRefs.current.forEach((card, index) => {
        if (!card) return
        const s = helixStyle(index - m.current, options)
        card.style.transform = s.transform
        card.style.opacity = String(s.opacity)
        card.style.zIndex = s.zIndex
        card.style.setProperty('--dim', s.dim.toFixed(3))
      })
      const nearest = Math.round(clamp(m.current, 0, count - 1))
      if (nearest !== m.active) {
        m.active = nearest
        setActive(nearest)
      }
    }
    const tick = (time: number) => {
      const dt = m.last ? Math.min(64, time - m.last) : 16
      m.last = time
      m.current = damp(m.current, m.target, dt, 110)
      layout()
      if (Math.abs(m.target - m.current) > 0.001) m.frame = requestAnimationFrame(tick)
      else {
        m.current = m.target
        m.frame = 0
        m.last = 0
        layout()
      }
    }
    const unsubscribe = subscribeScroll(() => {
      const outer = outerRef.current
      if (!outer) return
      const travel = outer.offsetHeight - window.innerHeight
      const start = outer.getBoundingClientRect().top + window.scrollY
      const p = travel > 0 ? clamp((window.scrollY - start) / travel) : 0
      m.target = p * (count - 1)
      if (barRef.current) barRef.current.style.transform = `scaleX(${p.toFixed(4)})`
      if (!m.frame) m.frame = requestAnimationFrame(tick)
    })
    const ro = new ResizeObserver(layout)
    ro.observe(stage)
    return () => {
      unsubscribe()
      ro.disconnect()
      cancelAnimationFrame(m.frame)
    }
  }, [pinned, count])

  return (
    <div
      ref={outerRef}
      className={styles.outer}
      data-pinned={pinned || undefined}
      data-accent={accent}
      style={pinned ? { height: `calc(100vh + ${(count - 1) * STEP_VH * 100}vh)` } : undefined}
    >
      <div className={styles.sticky}>
        <div className={`container ${styles.layout}`}>
          <div className={styles.head}>
            {header}
            {pinned && (
              <div className={styles.progressRow}>
                <span className={styles.count} aria-hidden="true">
                  {String(active + 1).padStart(2, '0')} / {String(count).padStart(2, '0')}
                </span>
                <span className={styles.progress} aria-hidden="true">
                  <span ref={barRef} className={styles.progressBar} />
                </span>
                <a href={`#${afterId}`} className={styles.skip}>
                  Skip
                </a>
              </div>
            )}
          </div>
          {/* As a swipe row it scrolls sideways, so it must be reachable by keyboard */}
          <ol ref={stageRef} className={styles.stage} aria-labelledby={headingId} tabIndex={pinned ? undefined : 0}>
            {items.map((item, index) => {
              const Icon = iconFor(item.icon)
              return (
                <li
                  key={item.id}
                  ref={(el) => {
                    cardRefs.current[index] = el
                  }}
                  className={styles.card}
                  data-active={(pinned && index === active) || undefined}
                >
                  <span className={styles.badge} aria-hidden="true">
                    {Icon ? <Icon size={30} strokeWidth={1.75} /> : String(index + 1).padStart(2, '0')}
                  </span>
                  <span className={styles.number} aria-hidden="true">
                    {String(index + 1).padStart(2, '0')}
                  </span>
                  <h3 className={styles.term}>{item.term}</h3>
                  {item.tagline && <p className={styles.tagline}>{item.tagline}</p>}
                </li>
              )
            })}
          </ol>
        </div>
      </div>
      <span id={afterId} tabIndex={-1} className={styles.after} />
    </div>
  )
}
