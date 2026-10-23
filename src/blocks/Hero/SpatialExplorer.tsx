'use client'
import { MoveHorizontal } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { Media } from '@/components/ui/Media'
import { damp, helixStyle, ringDistance } from '@/components/motion/helix'
import { subscribeScroll } from '@/components/motion/scroll'
import { useMediaQuery, useReducedMotion } from '@/components/motion/useReducedMotion'
import type { Media as MediaDoc } from '@/payload-types'
import styles from './SpatialExplorer.module.css'

type Slide = { id: string; image: MediaDoc; caption: string }

const DRAG_THRESHOLD = 8 // px before a press counts as a drag rather than a tap
const SCROLL_STEP = 160 // px of page scroll per photo while the hero is on screen
const MAX_TILT = 2 // degrees

/**
 * Spiral photo gallery. Photos sit on a shallow helix that turns as the page
 * scrolls and as the visitor drags or uses the arrow keys. Native scrolling is
 * never intercepted: the page scroll position is only read. Positions are
 * written straight to the DOM each frame; React state changes only when the
 * focused photo changes (for the caption).
 */
export function SpatialExplorer({ slides }: { slides: Slide[] }) {
  const count = slides.length
  const [active, setActive] = useState(0)
  const reduced = useReducedMotion()
  const compact = useMediaQuery('(max-width: 767px)', true)
  const finePointer = useMediaQuery('(pointer: fine)')
  const stageRef = useRef<HTMLDivElement>(null)
  const cardRefs = useRef<(HTMLLIElement | null)[]>([])
  const motion = useRef({ manual: 0, scroll: 0, current: 0, width: 0, frame: 0, last: 0, active: 0 })
  const kickRef = useRef<() => void>(() => undefined)
  const drag = useRef<{ x: number; start: number; dragging: boolean; pointerId: number } | null>(null)

  // Render loop: ease the current position toward manual + scroll, then lay out cards.
  useEffect(() => {
    const m = motion.current
    const stage = stageRef.current
    if (!stage || !count) return

    const layout = () => {
      const w = m.width || stage.clientWidth
      const options = compact
        ? { radius: w * 0.7, step: 34, rise: -w * 0.02, visible: 2.2 }
        : { radius: w * 0.5, step: 36, rise: -w * 0.03, visible: 2.6 }
      cardRefs.current.forEach((card, index) => {
        if (!card) return
        const s = helixStyle(ringDistance(index, m.current, count), options)
        card.style.transform = s.transform
        card.style.opacity = String(s.opacity)
        card.style.zIndex = s.zIndex
        card.style.setProperty('--dim', s.dim.toFixed(3))
        card.style.visibility = s.opacity === 0 ? 'hidden' : 'visible'
      })
      const nearest = ((Math.round(m.current) % count) + count) % count
      if (nearest !== m.active) {
        m.active = nearest
        setActive(nearest)
      }
    }

    const tick = (time: number) => {
      const dt = m.last ? Math.min(64, time - m.last) : 16
      m.last = time
      const target = m.manual + m.scroll
      m.current = reduced ? target : damp(m.current, target, dt, 90)
      layout()
      if (Math.abs(target - m.current) > 0.001) m.frame = requestAnimationFrame(tick)
      else {
        m.current = target
        m.frame = 0
        m.last = 0
      }
    }
    const kick = () => {
      if (!m.frame) m.frame = requestAnimationFrame(tick)
    }
    kickRef.current = kick

    const ro = new ResizeObserver(() => {
      m.width = stage.clientWidth
      layout()
    })
    ro.observe(stage)

    // Page scroll turns the spiral while the hero is on screen (read-only, never intercepted).
    const unsubscribe = reduced
      ? undefined
      : subscribeScroll(() => {
          const rect = stage.getBoundingClientRect()
          if (rect.bottom < -200 || rect.top > window.innerHeight + 200) return
          m.scroll = Math.max(0, window.scrollY) / SCROLL_STEP
          kick()
        })
    if (reduced) m.scroll = 0
    layout()
    return () => {
      ro.disconnect()
      unsubscribe?.()
      cancelAnimationFrame(m.frame)
      m.frame = 0
    }
  }, [count, compact, reduced])

  const nudge = (delta: number) => {
    const m = motion.current
    m.manual = Math.round(m.manual + m.scroll + delta) - m.scroll
    kickRef.current()
  }

  // Pointer tilt (fine pointers only, never with reduced motion).
  useEffect(() => {
    const stage = stageRef.current
    if (!stage || reduced || !finePointer) return
    const onMove = (event: PointerEvent) => {
      const rect = stage.getBoundingClientRect()
      const px = (event.clientX - rect.left) / rect.width - 0.5
      const py = (event.clientY - rect.top) / rect.height - 0.5
      stage.style.setProperty('--tilt-x', `${(-py * MAX_TILT).toFixed(2)}deg`)
      stage.style.setProperty('--tilt-y', `${(px * MAX_TILT).toFixed(2)}deg`)
    }
    const onLeave = () => {
      stage.style.setProperty('--tilt-x', '0deg')
      stage.style.setProperty('--tilt-y', '0deg')
    }
    stage.addEventListener('pointermove', onMove)
    stage.addEventListener('pointerleave', onLeave)
    return () => {
      stage.removeEventListener('pointermove', onMove)
      stage.removeEventListener('pointerleave', onLeave)
      onLeave()
    }
  }, [reduced, finePointer])

  const onPointerDown = (event: React.PointerEvent) => {
    if (event.button !== 0) return
    drag.current = { x: event.clientX, start: motion.current.manual, dragging: false, pointerId: event.pointerId }
  }
  const onPointerMove = (event: React.PointerEvent) => {
    const d = drag.current
    if (!d) return
    const dx = event.clientX - d.x
    if (!d.dragging && Math.abs(dx) > DRAG_THRESHOLD) {
      d.dragging = true
      stageRef.current?.setPointerCapture(d.pointerId)
    }
    if (d.dragging) {
      const width = motion.current.width || 600
      motion.current.manual = d.start - dx / (width * (compact ? 0.6 : 0.32))
      kickRef.current()
    }
  }
  const endDrag = (event: React.PointerEvent) => {
    const d = drag.current
    drag.current = null
    if (d?.dragging) {
      nudge(0) // settle on the nearest photo
      if (stageRef.current?.hasPointerCapture(event.pointerId)) stageRef.current.releasePointerCapture(event.pointerId)
    }
  }

  const onKeyDown = (event: React.KeyboardEvent) => {
    if (event.key === 'ArrowRight' || event.key === 'ArrowLeft') {
      event.preventDefault()
      nudge(event.key === 'ArrowRight' ? 1 : -1)
    }
  }

  const current = slides[active]

  return (
    <div className={styles.explorer} role="region" aria-roledescription="photo gallery" aria-label="Life at ISP in photographs">
      <div
        ref={stageRef}
        className={styles.stage}
        tabIndex={0}
        aria-describedby="hero-gallery-hint"
        onKeyDown={onKeyDown}
        onPointerDown={onPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={endDrag}
        onPointerCancel={endDrag}
      >
        <ul className={styles.ring}>
          {slides.map((slide, index) => (
            <li
              key={slide.id}
              ref={(el) => {
                cardRefs.current[index] = el
              }}
              className={styles.card}
              data-active={index === active || undefined}
              aria-current={index === active ? 'true' : undefined}
            >
              <span className={styles.frame}>
                <Media
                  media={slide.image}
                  priority={index === 0}
                  eager
                  maxSize="feature"
                  sizes="(min-width: 1024px) 34vw, 80vw"
                  className={styles.image}
                  alt={slide.image.alt}
                />
              </span>
            </li>
          ))}
        </ul>
      </div>

      <div className={styles.controls}>
        <p className={styles.caption} aria-live="polite">
          <span className={styles.count}>
            {String(active + 1).padStart(2, '0')} / {String(count).padStart(2, '0')}
          </span>
          <span>{current?.caption}</span>
        </p>
        <p id="hero-gallery-hint" className={styles.hint}>
          <MoveHorizontal size={16} aria-hidden="true" />
          <span>Scroll or drag to explore</span>
          <span className="visually-hidden">. When the gallery is focused, use the left and right arrow keys.</span>
        </p>
      </div>
    </div>
  )
}
