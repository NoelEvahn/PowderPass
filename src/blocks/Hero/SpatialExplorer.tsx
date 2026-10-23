'use client'
import { ChevronLeft, ChevronRight, LayoutGrid, Layers } from 'lucide-react'
import { useCallback, useEffect, useId, useRef, useState } from 'react'
import { Media } from '@/components/ui/Media'
import { useMediaQuery, useReducedMotion } from '@/components/motion/useReducedMotion'
import type { Media as MediaDoc } from '@/payload-types'
import styles from './SpatialExplorer.module.css'

type Slide = { id: string; image: MediaDoc; caption: string }
type Mode = 'spatial' | 'flat'

const DRAG_THRESHOLD = 8 // px before a press counts as a drag rather than a tap
const DRAG_STEP = 90 // px of drag per image
const MAX_TILT = 2 // degrees

/** Shortest signed distance from `active` to `index` around the ring. */
const ringOffset = (index: number, active: number, count: number) => {
  let d = index - active
  if (d > count / 2) d -= count
  if (d < -count / 2) d += count
  return d
}

/** Card transform for each mode. Values are in container-query units so they scale with the stage. */
function cardStyle(d: number, mode: Mode, index: number, count: number, compact: boolean): React.CSSProperties {
  if (mode === 'flat') {
    const cols = count > 6 ? 4 : 3
    const rows = Math.ceil(count / cols)
    const col = index % cols
    const row = Math.floor(index / cols)
    const x = ((col + 0.5) / cols - 0.5) * 100
    const y = ((row + 0.5) / rows - 0.5) * 100
    const scale = (1 / cols) * 1.42
    return { transform: `translate3d(calc(${x} * 1cqw), calc(${y} * 1cqh), 0) scale(${scale})`, opacity: 1, zIndex: 1 }
  }
  const abs = Math.abs(d)
  if (compact) {
    return { transform: `translate3d(calc(${d * 104} * 1cqw), 0, 0)`, opacity: abs === 0 ? 1 : 0, zIndex: 10 - abs }
  }
  // Shallow helix: neighbours recede, turn slightly and rise. Only three images are
  // prominent; the next pair sits faintly behind them to suggest the rest of the set.
  const x = d * (abs > 1 ? 22 : 27)
  const y = d * -3.5
  const z = -abs * 180
  const rotY = -d * 12
  const rotZ = d * 1.2
  return {
    transform: `translate3d(calc(${x} * 1cqw), calc(${y} * 1cqh), ${z}px) rotateY(${rotY}deg) rotateZ(${rotZ}deg)`,
    opacity: abs > 2 ? 0 : abs === 2 ? 0.45 : 1,
    zIndex: 10 - abs,
  }
}

export function SpatialExplorer({ slides }: { slides: Slide[] }) {
  const count = slides.length
  const [active, setActive] = useState(0)
  const [mode, setMode] = useState<Mode>('spatial')
  const reduced = useReducedMotion()
  const compact = useMediaQuery('(max-width: 767px)', true)
  const finePointer = useMediaQuery('(pointer: fine)')
  const stageRef = useRef<HTMLDivElement>(null)
  const drag = useRef<{ x: number; startActive: number; dragging: boolean; pointerId: number } | null>(null)
  const regionId = useId()

  const go = useCallback((next: number) => setActive(((next % count) + count) % count), [count])

  // Pointer tilt (fine pointers only, never with reduced motion).
  useEffect(() => {
    const stage = stageRef.current
    if (!stage || reduced || !finePointer || mode !== 'spatial') return
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
  }, [reduced, finePointer, mode])

  const onPointerDown = (event: React.PointerEvent) => {
    if (mode !== 'spatial' || event.button !== 0) return
    drag.current = { x: event.clientX, startActive: active, dragging: false, pointerId: event.pointerId }
  }
  const onPointerMove = (event: React.PointerEvent) => {
    const d = drag.current
    if (!d) return
    const dx = event.clientX - d.x
    if (!d.dragging && Math.abs(dx) > DRAG_THRESHOLD) {
      d.dragging = true
      stageRef.current?.setPointerCapture(d.pointerId)
    }
    if (d.dragging) go(d.startActive - Math.round(dx / DRAG_STEP))
  }
  const endDrag = (event: React.PointerEvent) => {
    const d = drag.current
    drag.current = null
    if (d?.dragging) {
      // Swallow the click that follows a drag so it does not select a card.
      const swallow = (e: MouseEvent) => {
        e.stopPropagation()
        e.preventDefault()
      }
      window.addEventListener('click', swallow, { capture: true, once: true })
      setTimeout(() => window.removeEventListener('click', swallow, { capture: true }), 0)
      if (stageRef.current?.hasPointerCapture(event.pointerId)) stageRef.current.releasePointerCapture(event.pointerId)
    }
  }

  const onKeyDown = (event: React.KeyboardEvent) => {
    if (mode !== 'spatial') return
    if (event.key === 'ArrowRight') {
      event.preventDefault()
      go(active + 1)
    } else if (event.key === 'ArrowLeft') {
      event.preventDefault()
      go(active - 1)
    }
  }

  const current = slides[active]

  return (
    <div
      className={styles.explorer}
      role="region"
      aria-roledescription="image explorer"
      aria-label="Life at ISP in photographs"
      data-mode={mode}
      onKeyDown={onKeyDown}
    >
      <div
        ref={stageRef}
        className={styles.stage}
        onPointerDown={onPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={endDrag}
        onPointerCancel={endDrag}
      >
        <ul className={styles.ring} id={regionId}>
          {slides.map((slide, index) => {
            const d = ringOffset(index, active, count)
            const isActive = index === active
            return (
              <li
                key={slide.id}
                className={styles.card}
                style={cardStyle(d, mode, index, count, compact)}
                data-active={isActive || undefined}
                aria-current={isActive ? 'true' : undefined}
                aria-hidden={mode === 'spatial' && !isActive && Math.abs(d) > 1 ? true : undefined}
              >
                <button
                  type="button"
                  className={styles.cardButton}
                  tabIndex={mode === 'flat' ? 0 : -1}
                  aria-label={`Show photo ${index + 1}: ${slide.caption}`}
                  onClick={() => {
                    setActive(index)
                    if (mode === 'flat') setMode('spatial')
                  }}
                >
                  <Media
                    media={slide.image}
                    priority={index === 0}
                    maxSize="feature"
                    sizes="(min-width: 1024px) 40vw, 90vw"
                    className={styles.image}
                    alt={slide.image.alt}
                  />
                </button>
              </li>
            )
          })}
        </ul>
      </div>

      <div className={styles.controls}>
        <p className={styles.caption} aria-live="polite">
          <span className={styles.count}>
            {String(active + 1).padStart(2, '0')} / {String(count).padStart(2, '0')}
          </span>
          <span>{current?.caption}</span>
        </p>
        <div className={styles.buttons}>
          <button type="button" className={styles.control} onClick={() => go(active - 1)} aria-controls={regionId} disabled={mode === 'flat'}>
            <ChevronLeft size={20} aria-hidden="true" />
            <span className="visually-hidden">Previous photo</span>
          </button>
          <button type="button" className={styles.control} onClick={() => go(active + 1)} aria-controls={regionId} disabled={mode === 'flat'}>
            <ChevronRight size={20} aria-hidden="true" />
            <span className="visually-hidden">Next photo</span>
          </button>
          <button
            type="button"
            className={`${styles.control} ${styles.modeToggle}`}
            aria-pressed={mode === 'flat'}
            onClick={() => setMode(mode === 'flat' ? 'spatial' : 'flat')}
          >
            {mode === 'flat' ? <Layers size={18} aria-hidden="true" /> : <LayoutGrid size={18} aria-hidden="true" />}
            <span>{mode === 'flat' ? 'Explorer view' : 'All photos'}</span>
          </button>
        </div>
      </div>
    </div>
  )
}
