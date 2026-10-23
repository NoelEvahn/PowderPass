'use client'
import { useEffect } from 'react'
import { subscribeScroll } from './scroll'

const QUERY = '(prefers-reduced-motion: reduce)'

/**
 * Gentle depth for large photographs: [data-parallax] elements drift against
 * the scroll while on screen (±`data-parallax` px, default 36). The element is
 * slightly enlarged in CSS so its frame never shows an edge. Off with reduced motion.
 */
export function ParallaxObserver() {
  useEffect(() => {
    const mql = window.matchMedia(QUERY)
    let unsubscribe: (() => void) | undefined
    let elements: HTMLElement[] = []
    const scan = () => {
      elements = Array.from(document.querySelectorAll<HTMLElement>('[data-parallax]'))
    }
    const start = () => {
      unsubscribe?.()
      if (mql.matches) {
        elements.forEach((el) => (el.style.transform = ''))
        return
      }
      unsubscribe = subscribeScroll(() => {
        const vh = window.innerHeight
        for (const el of elements) {
          const rect = el.getBoundingClientRect()
          if (rect.bottom < -100 || rect.top > vh + 100) continue
          const range = Number(el.dataset.parallax) || 36
          const p = (rect.top + rect.height / 2 - vh / 2) / (vh / 2 + rect.height / 2)
          el.style.transform = `translate3d(0, ${(-p * range).toFixed(1)}px, 0) scale(1.12)`
        }
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
