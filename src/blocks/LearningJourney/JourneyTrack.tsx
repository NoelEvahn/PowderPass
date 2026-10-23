'use client'
import { useEffect, useRef, useState } from 'react'
import { ArrowLink } from '@/components/ui/ArrowLink'
import { Media } from '@/components/ui/Media'
import { clamp, subscribeScroll } from '@/components/motion/scroll'
import { useMediaQuery, useReducedMotion } from '@/components/motion/useReducedMotion'
import type { Media as MediaDoc } from '@/payload-types'
import styles from './Journey.module.css'

export type JourneyPanel = {
  id: string
  name: string
  ages: string
  yearGroups: string | null
  tagline: string
  summary: string
  highlights: string[]
  accent: string
  image: MediaDoc | null
  href: string
}

type Props = {
  headingId: string
  anchorId?: string
  eyebrow?: string | null
  heading: string
  intro?: string | null
  panels: JourneyPanel[]
  horizontal: boolean
}

/**
 * Down → across → down. Native vertical scroll inside a tall wrapper is mapped
 * to the track's horizontal position: D = track overflow, x = −D × progress.
 * Pinning is only used on large, tall viewports without reduced motion;
 * everywhere else the same markup renders as an ordinary vertical stack.
 */
export function JourneyTrack({ headingId, anchorId, eyebrow, heading, intro, panels, horizontal }: Props) {
  const reduced = useReducedMotion()
  const largeEnough = useMediaQuery('(min-width: 1024px) and (min-height: 680px)')
  const pinned = horizontal && largeEnough && !reduced && panels.length > 1

  const outerRef = useRef<HTMLDivElement>(null)
  const viewportRef = useRef<HTMLDivElement>(null)
  const trackRef = useRef<HTMLOListElement>(null)
  const barRef = useRef<HTMLSpanElement>(null)
  const [distance, setDistance] = useState(0)
  const [activeIndex, setActiveIndex] = useState(0)
  const skipTargetId = `${headingId}-after`

  // Measure overflow; re-measure when layout, fonts or images change size.
  useEffect(() => {
    if (!pinned) {
      setDistance(0)
      return
    }
    const measure = () => {
      const track = trackRef.current
      const viewport = viewportRef.current
      if (track && viewport) setDistance(Math.max(0, track.scrollWidth - viewport.clientWidth))
    }
    measure()
    const ro = new ResizeObserver(measure)
    if (trackRef.current) ro.observe(trackRef.current)
    if (viewportRef.current) ro.observe(viewportRef.current)
    document.fonts?.ready.then(measure).catch(() => undefined)
    return () => ro.disconnect()
  }, [pinned, panels.length])

  // Map scroll progress to translateX. One subscription to the shared scroll owner.
  useEffect(() => {
    const track = trackRef.current
    if (!pinned || distance <= 0 || !track) {
      if (track) track.style.transform = ''
      return
    }
    return subscribeScroll(() => {
      const outer = outerRef.current
      if (!outer) return
      const start = outer.getBoundingClientRect().top + window.scrollY
      const p = clamp((window.scrollY - start) / distance)
      track.style.transform = `translate3d(${(-distance * p).toFixed(1)}px, 0, 0)`
      if (barRef.current) barRef.current.style.transform = `scaleX(${p.toFixed(4)})`
      setActiveIndex(Math.min(panels.length - 1, Math.round(p * (panels.length - 1))))
    })
  }, [pinned, distance, panels.length])

  // Keyboard users tabbing into an off-screen panel: scroll the page so that panel is in view.
  const onFocus = (event: React.FocusEvent) => {
    if (!pinned || distance <= 0) return
    const panel = (event.target as HTMLElement).closest<HTMLElement>('[data-panel-index]')
    const first = trackRef.current?.firstElementChild as HTMLElement | null
    const outer = outerRef.current
    if (!panel || !first || !outer) return
    const x = clamp(panel.offsetLeft - first.offsetLeft, 0, distance)
    const start = outer.getBoundingClientRect().top + window.scrollY
    window.scrollTo({ top: start + x, behavior: 'instant' })
  }

  return (
    <section
      id={anchorId}
      aria-labelledby={headingId}
      className={styles.section}
      data-pinned={pinned || undefined}
    >
      <div ref={outerRef} className={styles.outer} style={pinned ? { height: `calc(100vh + ${distance}px)` } : undefined}>
        <div className={styles.sticky}>
          <div className={`container ${styles.head}`}>
            <div className={styles.titles}>
              {eyebrow && <p className="eyebrow">{eyebrow}</p>}
              <h2 id={headingId} className={styles.heading}>
                {heading}
              </h2>
            </div>
            <div className={styles.meta}>
              {intro && <p className={styles.intro}>{intro}</p>}
              {pinned && (
                <div className={styles.progressRow}>
                  <span className={styles.count} aria-hidden="true">
                    {String(activeIndex + 1).padStart(2, '0')} / {String(panels.length).padStart(2, '0')}
                  </span>
                  <span className={styles.progress} aria-hidden="true">
                    <span ref={barRef} className={styles.progressBar} />
                  </span>
                  <a href={`#${skipTargetId}`} className={styles.skip}>
                    Skip the learning journey
                  </a>
                </div>
              )}
            </div>
          </div>

          <div ref={viewportRef} className={styles.viewport} onFocus={onFocus}>
            <ol ref={trackRef} className={styles.track}>
              {panels.map((panel, index) => (
                <li key={panel.id} className={styles.panel} data-panel-index={index} data-accent={panel.accent}>
                  <div className={styles.media}>
                    <Media media={panel.image} maxSize="feature" sizes="(min-width: 1024px) 34vw, 100vw" className={styles.image} />
                  </div>
                  <div className={styles.body}>
                    <p className={styles.stage}>
                      <span className={styles.stageName}>{panel.name}</span>
                      <span className={styles.ages}>
                        Ages {panel.ages}
                        {panel.yearGroups && ` · ${panel.yearGroups}`}
                      </span>
                    </p>
                    <h3 className={styles.tagline}>{panel.tagline}</h3>
                    <p className={styles.summary}>{panel.summary}</p>
                    {panel.highlights.length > 0 && (
                      <ul className={styles.highlights}>
                        {panel.highlights.map((h) => (
                          <li key={h}>{h}</li>
                        ))}
                      </ul>
                    )}
                    <ArrowLink href={panel.href}>Explore {panel.name}</ArrowLink>
                  </div>
                </li>
              ))}
            </ol>
          </div>
        </div>
      </div>
      <span id={skipTargetId} tabIndex={-1} className={styles.after} />
    </section>
  )
}
