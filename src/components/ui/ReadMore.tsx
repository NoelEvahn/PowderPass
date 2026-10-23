'use client'
import { ChevronDown } from 'lucide-react'
import { useId, useRef, useState } from 'react'
import styles from './ReadMore.module.css'

/**
 * Long text opens at a comfortable height with a down-arrow "Read more"
 * control. Without JavaScript the whole text shows (the collapsed style only
 * applies under html.js). The full text stays in the document either way, so
 * search engines and screen readers get all of it.
 */
export function ReadMore({ children, context }: { children: React.ReactNode; /** Names the text for screen readers, e.g. the section heading. */ context?: string | null }) {
  const [open, setOpen] = useState(false)
  const id = useId()
  const ref = useRef<HTMLDivElement>(null)

  const toggle = () => {
    const next = !open
    setOpen(next)
    // When closing, bring the start of the text back into view.
    if (!next) ref.current?.scrollIntoView({ block: 'start', behavior: 'smooth' })
  }

  return (
    <div className={styles.wrap} data-open={open || undefined}>
      <div ref={ref} id={id} className={styles.content}>
        {children}
      </div>
      <button type="button" className={styles.toggle} aria-expanded={open} aria-controls={id} onClick={toggle}>
        <span>
          {open ? 'Show less' : 'Read more'}
          {context && <span className="visually-hidden">: {context}</span>}
        </span>
        <span className={styles.arrow} aria-hidden="true">
          <ChevronDown size={20} />
        </span>
      </button>
    </div>
  )
}
