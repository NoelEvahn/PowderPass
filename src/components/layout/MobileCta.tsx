'use client'
import { CalendarCheck } from 'lucide-react'
import Link from 'next/link'
import { usePathname } from 'next/navigation'
import styles from './MobileCta.module.css'

/**
 * Persistent "Visit & Enquire" action on small screens. Hidden on the visit page
 * itself; the footer reserves matching space so no content is ever covered.
 */
export function MobileCta({ href, label }: { href: string; label: string }) {
  const pathname = usePathname()
  if (pathname === href) return null
  return (
    <div className={styles.bar}>
      <Link href={href} className={styles.button}>
        <CalendarCheck size={20} aria-hidden="true" />
        {label}
      </Link>
    </div>
  )
}
