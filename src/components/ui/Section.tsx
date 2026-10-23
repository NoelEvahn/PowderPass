import type { ReactNode } from 'react'
import styles from './Section.module.css'

type Props = {
  children: ReactNode
  tone?: 'plain' | 'tint' | 'ink' | null
  id?: string | null
  labelledBy?: string
  className?: string
  spacing?: 'default' | 'tight' | 'none'
}

export function Section({ children, tone = 'plain', id, labelledBy, className, spacing = 'default' }: Props) {
  return (
    <section
      id={id ?? undefined}
      aria-labelledby={labelledBy}
      data-tone={tone ?? 'plain'}
      className={[styles.section, styles[tone ?? 'plain'], styles[spacing], className].filter(Boolean).join(' ')}
    >
      {children}
    </section>
  )
}
