'use client'
import { useEffect } from 'react'
import { subscribeScroll } from './scroll'

const QUERY = '(prefers-reduced-motion: reduce)'

/**
 * Headings marked [data-drift] glide in from one side as they rise into view,
 * hold still through the middle of the screen, then slide out to the other
 * side as they scroll away. Sticky headings (beside long text) therefore ride
 * down with the page and only move sideways once their section ends.
 * Alternate headings travel in opposite directions. Off with reduced motion.
 */
export function HeadingDrift() {
  useEffect(() => {
    const mql = window.matchMedia(QUERY)
    let unsubscribe: (() => void) | undefined
    let headings: HTMLElement[] = []
    const scan = () => {
      headings = Array.from(document.querySelectorAll<HTMLElement>('[data-drift]'))
    }
    const start = () => {
      unsubscribe?.()
      if (mql.matches) {
        headings.forEach((el) => (el.style.transform = ''))
        return
      }
      unsubscribe = subscribeScroll(() => {
        const vh = window.innerHeight
        const vw = window.innerWidth
        headings.forEach((el, i) => {
          const rect = el.getBoundingClientRect()
          if (rect.bottom < -200 || rect.top > vh + 200) return
          // p: 1 when the heading's centre sits at the bottom edge, −1 at the top edge.
          const p = (rect.top + rect.height / 2 - vh / 2) / (vh / 2)
          const enter = Math.min(1, Math.max(0, p - 0.55) / 0.45) // 1 → 0 as it rises through the lowest part of the screen
          const exit = Math.min(1, Math.max(0, -p - 0.7) / 0.3) // 0 → 1 only once it is leaving through the top
          const dir = i % 2 === 0 ? 1 : -1
          const x = (enter * 0.08 - exit * 0.14) * vw * dir
          el.style.transform = x ? `translate3d(${x.toFixed(1)}px, 0, 0)` : ''
        })
      })
    }
    scan()
    start()
    const mo = new MutationObserver(scan)
    mo.observe(document.body, { childList: true, subtree: true })
    mql.addEventListener('change', start)
    return () => {
      unsubscribe?.()
      mo.disconnect()
      mql.removeEventListener('change', start)
    }
  }, [])
  return null
}
