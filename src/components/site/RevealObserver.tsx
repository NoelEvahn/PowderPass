'use client'

import { useEffect } from 'react'

/**
 * Soft one-time fade for elements marked data-reveal. Content is visible without
 * JavaScript and with reduced motion: the hidden state is only applied here.
 */
export const RevealObserver = () => {
  useEffect(() => {
    const motionOK = window.matchMedia('(prefers-reduced-motion: no-preference)').matches
    if (!motionOK || !('IntersectionObserver' in window)) return
    const root = document.documentElement
    const observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (entry.isIntersecting) {
            entry.target.classList.add('is-visible')
            observer.unobserve(entry.target)
          }
        }
      },
      { rootMargin: '0px 0px -8% 0px', threshold: 0.05 },
    )
    const observeAll = () => {
      document.querySelectorAll('[data-reveal]:not(.is-visible)').forEach((el) => {
        const rect = el.getBoundingClientRect()
        // Anything already on screen stays as it is: nothing animates in at load.
        if (rect.top < window.innerHeight) el.classList.add('is-visible')
        else observer.observe(el)
      })
    }
    observeAll()
    root.classList.add('reveal-ready')
    const mo = new MutationObserver(observeAll)
    mo.observe(document.body, { childList: true, subtree: true })
    return () => {
      observer.disconnect()
      mo.disconnect()
    }
  }, [])
  return null
}
