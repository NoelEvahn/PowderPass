'use client'
import { useEffect, useRef } from 'react'
import { subscribeScroll } from './scroll'

/** Thin reading-progress line under the header. Decorative; hidden from assistive technology. */
export function ScrollProgress() {
  const ref = useRef<HTMLDivElement>(null)
  useEffect(
    () =>
      subscribeScroll(() => {
        const max = document.documentElement.scrollHeight - window.innerHeight
        const p = max > 0 ? Math.min(1, Math.max(0, window.scrollY / max)) : 0
        if (ref.current) ref.current.style.transform = `scaleX(${p.toFixed(4)})`
      }),
    [],
  )
  return <div ref={ref} className="scroll-progress" aria-hidden="true" />
}
