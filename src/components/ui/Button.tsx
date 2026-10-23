import Link from 'next/link'
import type { ReactNode } from 'react'
import styles from './Button.module.css'

type Props = {
  href: string
  children: ReactNode
  variant?: 'primary' | 'secondary' | 'light'
  icon?: ReactNode
  external?: boolean
  newTab?: boolean
  className?: string
}

/** Primary/secondary actions. `icon` renders the joined icon-and-label style. */
export function Button({ href, children, variant = 'primary', icon, external, newTab, className }: Props) {
  const cls = [styles.button, styles[variant], icon ? styles.joined : '', className].filter(Boolean).join(' ')
  const inner = (
    <>
      {icon && (
        <span className={styles.iconCell} aria-hidden="true">
          {icon}
        </span>
      )}
      <span className={styles.text}>{children}</span>
      {newTab && <span className="visually-hidden"> (opens in a new tab)</span>}
    </>
  )
  if (external || newTab || !href.startsWith('/')) {
    return (
      <a className={cls} href={href} {...(newTab ? { target: '_blank', rel: 'noopener noreferrer' } : {})}>
        {inner}
      </a>
    )
  }
  return (
    <Link className={cls} href={href}>
      {inner}
    </Link>
  )
}
