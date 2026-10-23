import { ChevronRight } from 'lucide-react'
import Link from 'next/link'
import styles from './Breadcrumbs.module.css'

export type Crumb = { label: string; href: string }

export function Breadcrumbs({ items, current }: { items: Crumb[]; current: string }) {
  return (
    <nav aria-label="Breadcrumb" className={`container ${styles.nav}`}>
      <ol className={styles.list}>
        <li>
          <Link href="/">Home</Link>
        </li>
        {items.map((item) => (
          <li key={item.href}>
            <ChevronRight size={14} aria-hidden="true" />
            <Link href={item.href}>{item.label}</Link>
          </li>
        ))}
        <li>
          <ChevronRight size={14} aria-hidden="true" />
          <span aria-current="page">{current}</span>
        </li>
      </ol>
    </nav>
  )
}
