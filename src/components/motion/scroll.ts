'use client'

/**
 * The single scroll owner for the site. Scroll-driven scenes subscribe here
 * instead of attaching their own listeners, so all reads happen once per frame.
 * Native scrolling is never intercepted.
 */
type Listener = () => void

const listeners = new Set<Listener>()
let ticking = false
let attached = false

const run = () => {
  ticking = false
  listeners.forEach((fn) => fn())
}

const onScroll = () => {
  if (!ticking) {
    ticking = true
    requestAnimationFrame(run)
  }
}

export function subscribeScroll(fn: Listener): () => void {
  listeners.add(fn)
  if (!attached) {
    window.addEventListener('scroll', onScroll, { passive: true })
    window.addEventListener('resize', onScroll, { passive: true })
    attached = true
  }
  fn()
  return () => {
    listeners.delete(fn)
    if (!listeners.size && attached) {
      window.removeEventListener('scroll', onScroll)
      window.removeEventListener('resize', onScroll)
      attached = false
    }
  }
}

export const clamp = (value: number, min = 0, max = 1) => Math.min(max, Math.max(min, value))

/** Map p from [start, end] onto [0, 1]. */
export const segment = (p: number, start: number, end: number) => clamp((p - start) / (end - start))

/** Smooth phase curve used for scroll choreography. */
export const easeInOut = (t: number) => (t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2)
