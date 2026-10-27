'use client'

import React, { useEffect, useRef } from 'react'

/**
 * The home page's one signature scene: the section holds still while each word
 * brightens in turn with an underline. Without JavaScript, on small screens or
 * with reduced motion, every word is simply shown lit and nothing is pinned.
 */
export const LitStatement = ({ words, caption }: { words: string[]; caption?: string }) => {
  const sectionRef = useRef<HTMLElement>(null)

  useEffect(() => {
    const section = sectionRef.current
    if (!section) return
    const motion = window.matchMedia('(prefers-reduced-motion: no-preference) and (min-height: 560px)')
    let frame = 0

    const update = () => {
      frame = 0
      const rect = section.getBoundingClientRect()
      const travel = section.offsetHeight - window.innerHeight
      const p = travel > 0 ? Math.min(1, Math.max(0, -rect.top / travel)) : 1
      // Words light between 10% and 80% of the travel.
      const lit = Math.min(1, Math.max(0, (p - 0.1) / 0.7))
      section.style.setProperty('--lit', lit.toFixed(3))
    }
    const onScroll = () => {
      if (!frame) frame = requestAnimationFrame(update)
    }
    const apply = () => {
      if (motion.matches) {
        section.dataset.lit = 'scroll'
        update()
        window.addEventListener('scroll', onScroll, { passive: true })
        window.addEventListener('resize', onScroll)
      } else {
        delete section.dataset.lit
        window.removeEventListener('scroll', onScroll)
        window.removeEventListener('resize', onScroll)
      }
    }
    apply()
    motion.addEventListener('change', apply)
    return () => {
      motion.removeEventListener('change', apply)
      window.removeEventListener('scroll', onScroll)
      window.removeEventListener('resize', onScroll)
      if (frame) cancelAnimationFrame(frame)
    }
  }, [])

  const n = words.length
  return (
    <section ref={sectionRef} className="lit" aria-label={words.join(' ')} style={{ ['--n' as string]: n }}>
      <div className="lit__sticky">
        <div className="container">
          <p className="lit__words" aria-hidden="true">
            {words.map((w, i) => (
              <span key={w} className="lit__word" style={{ ['--i' as string]: i }}>
                {w}
              </span>
            ))}
          </p>
          {caption ? <p className="lit__caption">{caption}</p> : null}
        </div>
      </div>
    </section>
  )
}
