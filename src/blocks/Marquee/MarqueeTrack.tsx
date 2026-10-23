'use client'
import { Pause, Play } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { useReducedMotion } from '@/components/motion/useReducedMotion'
import styles from './Marquee.module.css'

/**
 * Slow decorative word band (motion level 2). Pauses on hover, focus, when
 * off-screen and on request; reduced motion shows one static line. The
 * readable list is provided once; the moving copies are aria-hidden.
 */
const PIXELS_PER_SECOND = 22

export function MarqueeTrack({ label, items }: { label: string; items: string[] }) {
  const reduced = useReducedMotion()
  const [paused, setPaused] = useState(false)
  const [visible, setVisible] = useState(true)
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const el = ref.current
    if (!el) return
    const io = new IntersectionObserver(([entry]) => setVisible(entry.isIntersecting))
    io.observe(el)
    // Constant speed regardless of content length.
    const group = el.querySelector<HTMLElement>('[data-group]')
    const ro = new ResizeObserver(() => {
      if (group) el.style.setProperty('--marquee-duration', `${(group.offsetWidth / PIXELS_PER_SECOND).toFixed(1)}s`)
    })
    if (group) ro.observe(group)
    return () => {
      io.disconnect()
      ro.disconnect()
    }
  }, [])

  const running = !reduced && !paused && visible

  return (
    <div ref={ref} className={styles.band} data-running={running || undefined} data-static={reduced || undefined}>
      <ul className="visually-hidden" aria-label={label}>
        {items.map((item) => (
          <li key={item}>{item}</li>
        ))}
      </ul>
      <div className={styles.viewport} aria-hidden="true">
        <div className={styles.track}>
          {[0, 1].map((copy) => (
            <span key={copy} className={styles.group} data-group={copy === 0 ? '' : undefined}>
              {items.map((item) => (
                <span key={item} className={styles.item}>
                  {item}
                </span>
              ))}
            </span>
          ))}
        </div>
      </div>
      {!reduced && (
        <button type="button" className={styles.toggle} onClick={() => setPaused((p) => !p)} aria-pressed={paused}>
          {paused ? <Play size={16} aria-hidden="true" /> : <Pause size={16} aria-hidden="true" />}
          <span className="visually-hidden">{paused ? 'Play moving words' : 'Pause moving words'}</span>
        </button>
      )}
    </div>
  )
}
