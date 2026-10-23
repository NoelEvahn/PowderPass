import type { ReactNode } from 'react'
import styles from './SectionHeader.module.css'
import { Words } from '@/components/motion/Words'

type Props = {
  id: string
  eyebrow?: string | null
  heading?: string | null
  intro?: string | null
  align?: 'start' | 'split'
  children?: ReactNode
}

/** Eyebrow + H2 + intro. The H2 id labels the parent section for assistive tech. */
export function SectionHeader({ id, eyebrow, heading, intro, align = 'start', children }: Props) {
  if (!heading && !eyebrow) return null
  return (
    <header className={[styles.header, styles[align]].join(' ')}>
      {eyebrow && (
        // Large, faint copy of the label that slides slowly with the scroll: background texture only.
        <span className={styles.ghost} aria-hidden="true" data-parallax-x="70" data-text={eyebrow} />
      )}
      <div className={styles.titles}>
        {eyebrow && <p className="eyebrow">{eyebrow}</p>}
        {heading && (
          <h2 id={id} className={styles.heading} data-reveal="words">
            <Words text={heading} />
          </h2>
        )}
      </div>
      {(intro || children) && (
        <div className={styles.aside}>
          {intro && <p className="lead">{intro}</p>}
          {children}
        </div>
      )}
    </header>
  )
}
