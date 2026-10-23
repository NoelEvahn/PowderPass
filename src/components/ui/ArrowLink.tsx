import { ArrowRight, ArrowUpRight } from 'lucide-react'
import Link from 'next/link'
import type { ReactNode } from 'react'
import styles from './ArrowLink.module.css'

type Props = {
  href: string
  children: ReactNode
  external?: boolean
  newTab?: boolean
  className?: string
}

/**
 * The site's single link response (motion level 1): the label rolls up to a
 * duplicate and the arrow is replaced by a second arrow. Hover and keyboard
 * focus share the effect; the duplicate label is hidden from assistive tech.
 */
export function ArrowLink({ href, children, external, newTab, className }: Props) {
  const Icon = external ? ArrowUpRight : ArrowRight
  const content = (
    <>
      <span className={styles.label}>
        <span className={styles.labelInner}>{children}</span>
        <span className={styles.labelClone} aria-hidden="true">
          {children}
        </span>
      </span>
      <span className={styles.icon} aria-hidden="true">
        <Icon className={styles.iconA} size={18} strokeWidth={2} />
        <Icon className={styles.iconB} size={18} strokeWidth={2} />
      </span>
      {newTab && <span className="visually-hidden"> (opens in a new tab)</span>}
    </>
  )
  const cls = [styles.link, className].filter(Boolean).join(' ')
  if (external || newTab || !href.startsWith('/')) {
    return (
      <a href={href} className={cls} {...(newTab ? { target: '_blank', rel: 'noopener noreferrer' } : {})}>
        {content}
      </a>
    )
  }
  return (
    <Link href={href} className={cls}>
      {content}
    </Link>
  )
}
