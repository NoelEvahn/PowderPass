import Link from 'next/link'
import { ArrowLink } from '@/components/ui/ArrowLink'
import { Button } from '@/components/ui/Button'
import styles from './not-found.module.css'

const ROUTES = [
  { label: 'Learning stages', href: '/learning' },
  { label: 'Admissions', href: '/admissions' },
  { label: 'Fees', href: '/admissions/fees' },
  { label: 'Visit & Enquire', href: '/admissions/visit' },
  { label: 'Contact', href: '/contact' },
]

export default function NotFound() {
  return (
    <section className={`container ${styles.page}`} aria-labelledby="page-title">
      <p className={styles.code} aria-hidden="true">
        4<span>@</span>4
      </p>
      <div className={styles.text}>
        <p className="eyebrow">Page not found</p>
        <h1 id="page-title" className={styles.title}>
          We could not find that page.
        </h1>
        <p className="lead">
          It may have moved when the website was redesigned, or the address may be mistyped. Try searching, or choose one of the
          most-visited pages below.
        </p>
        <form role="search" action="/search" method="get" className={styles.form}>
          <label htmlFor="nf-search" className="visually-hidden">
            Search the website
          </label>
          <input id="nf-search" name="q" type="search" placeholder="Search the website" className={styles.input} />
          <button type="submit" className={styles.button}>
            Search
          </button>
        </form>
        <ul className={styles.links}>
          {ROUTES.map((route) => (
            <li key={route.href}>
              <ArrowLink href={route.href}>{route.label}</ArrowLink>
            </li>
          ))}
        </ul>
        <div>
          <Button href="/">Go to the home page</Button>
        </div>
        <p className={styles.small}>
          Looking for a page from the previous ISP website? <Link href="/contact">Let us know</Link> and we will help.
        </p>
      </div>
    </section>
  )
}
