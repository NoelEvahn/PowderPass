'use client'
import { useEffect } from 'react'

/**
 * Marks [data-reveal] elements as revealed when they enter the viewport (once).
 * A MutationObserver picks up content added by navigation or live preview.
 */
export function RevealObserver() {
  useEffect(() => {
    const win = window as Window & { __ispReveal?: boolean }
    win.__ispReveal = true
    document.documentElement.classList.add('js')
    if (!('IntersectionObserver' in window)) {
      document.querySelectorAll('[data-reveal]').forEach((el) => el.setAttribute('data-revealed', ''))
      return
    }

    const io = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (entry.isIntersecting) {
            entry.target.setAttribute('data-revealed', '')
            io.unobserve(entry.target)
          }
        }
      },
      { rootMargin: '0px 0px -8% 0px', threshold: 0.08 },
    )

    const scan = () => document.querySelectorAll('[data-reveal]:not([data-revealed])').forEach((el) => io.observe(el))
    scan()
    const mo = new MutationObserver(scan)
    mo.observe(document.body, { childList: true, subtree: true })
    return () => {
      io.disconnect()
      mo.disconnect()
    }
  }, [])
  return null
}
