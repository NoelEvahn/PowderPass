'use client'
import { Children, useEffect, useRef, useState } from 'react'
import { clamp, subscribeScroll } from './scroll'
import { useMediaQuery, useReducedMotion } from './useReducedMotion'
import styles from './HorizontalChapter.module.css'

type Props = {
  headingId: string
  header: React.ReactNode
  /** Each child becomes one panel in the row. */
  children: React.ReactNode
  /** Rendered list element: ordered for sequences such as steps. */
  as?: 'ul' | 'ol'
  skipLabel?: string
}

/**
 * Down → across → down, for any row of cards. Native vertical scroll inside a
 * tall wrapper moves the row sideways (D = overflow, x = −D × progress), then
 * releases. Small or short screens and reduced motion get a native swipe row.
 */
export function HorizontalChapter({ headingId, header, children, as: List = 'ul', skipLabel = 'Skip this section' }: Props) {
  const reduced = useReducedMotion()
  const largeEnough = useMediaQuery('(min-width: 1024px) and (min-height: 640px)')
  const items = Children.toArray(children)
  const outerRef = useRef<HTMLDivElement>(null)
  const viewportRef = useRef<HTMLDivElement>(null)
  const trackRef = useRef<HTMLOListElement & HTMLUListElement>(null)
  const barRef = useRef<HTMLSpanElement>(null)
  const [overflow, setOverflow] = useState(0)
  // Pin only when the row really is wider than the space for it; otherwise it simply sits in the page.
  const pinned = largeEnough && !reduced && items.length > 1 && overflow > 0
  const distance = pinned ? overflow : 0
  const afterId = `${headingId}-after`

  // Overflow = row width minus the content box of the viewport (its side padding aligns the row with the page).
  useEffect(() => {
    const measure = () => {
      const track = trackRef.current
      const viewport = viewportRef.current
      if (!track || !viewport) return
      const style = getComputedStyle(viewport)
      const inner = viewport.clientWidth - parseFloat(style.paddingLeft) - parseFloat(style.paddingRight)
      setOverflow(Math.max(0, Math.ceil(track.scrollWidth - inner)))
    }
    measure()
    const ro = new ResizeObserver(measure)
    if (trackRef.current) ro.observe(trackRef.current)
    if (viewportRef.current) ro.observe(viewportRef.current)
    return () => ro.disconnect()
  }, [items.length])

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
    })
  }, [pinned, distance])

  // Keyboard users tabbing to an off-screen card: scroll the page so it comes into view.
  const onFocus = (event: React.FocusEvent) => {
    if (!pinned || distance <= 0) return
    const item = (event.target as HTMLElement).closest<HTMLElement>('[data-chapter-item]')
    const first = trackRef.current?.firstElementChild as HTMLElement | null
    const outer = outerRef.current
    if (!item || !first || !outer) return
    const x = clamp(item.offsetLeft - first.offsetLeft, 0, distance)
    window.scrollTo({ top: outer.getBoundingClientRect().top + window.scrollY + x, behavior: 'instant' })
  }

  return (
    <div
      ref={outerRef}
      className={styles.outer}
      data-pinned={pinned || undefined}
      style={pinned ? { height: `calc(100vh + ${distance}px)` } : undefined}
    >
      <div className={styles.sticky}>
        <div className={`container ${styles.head}`}>
          {header}
          {pinned && (
            <div className={styles.progressRow}>
              <span className={styles.progress} aria-hidden="true">
                <span ref={barRef} className={styles.progressBar} />
              </span>
              <a href={`#${afterId}`} className={styles.skip}>
                {skipLabel}
              </a>
            </div>
          )}
        </div>
        <div ref={viewportRef} className={styles.viewport} onFocus={onFocus}>
          <List ref={trackRef} className={styles.track} aria-labelledby={headingId}>
            {items.map((child, index) => (
              <li key={index} className={styles.item} data-chapter-item>
                {child}
              </li>
            ))}
          </List>
        </div>
      </div>
      <span id={afterId} tabIndex={-1} className={styles.after} />
    </div>
  )
}
