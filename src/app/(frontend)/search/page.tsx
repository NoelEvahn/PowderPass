import type { Metadata } from 'next'
import Link from 'next/link'
import { Search } from 'lucide-react'
import { getPayloadClient } from '@/lib/payload'
import { MIN_QUERY_LENGTH, normaliseQuery, searchSite } from '@/lib/search'
import styles from './search.module.css'

type Props = { searchParams: Promise<{ q?: string }> }

export const metadata: Metadata = { title: 'Search', robots: { index: false, follow: true } }

const SUGGESTIONS = [
  { label: 'Fees', href: '/admissions/fees' },
  { label: 'How to apply', href: '/admissions/apply' },
  { label: 'Visit & Enquire', href: '/admissions/visit' },
  { label: 'Learning stages', href: '/learning' },
  { label: 'Parent questions', href: '/admissions/faqs' },
  { label: 'Contact', href: '/contact' },
]

/** Full results page. Works without JavaScript (plain GET form). */
export default async function SearchPage({ searchParams }: Props) {
  const q = normaliseQuery((await searchParams).q)
  const results = q.length >= MIN_QUERY_LENGTH ? await searchSite(await getPayloadClient(), q, 40) : []

  return (
    <section className={`container ${styles.page}`} aria-labelledby="page-title">
      <h1 id="page-title" className={styles.title}>
        {q ? `Search results for “${q}”` : 'Search the website'}
      </h1>
      <form role="search" action="/search" method="get" className={styles.form}>
        <label htmlFor="search-page-input" className="visually-hidden">
          Search the website
        </label>
        <input id="search-page-input" name="q" type="search" defaultValue={q} minLength={MIN_QUERY_LENGTH} className={styles.input} placeholder="Search fees, curriculum, visits…" />
        <button type="submit" className={styles.button}>
          <Search size={20} aria-hidden="true" /> Search
        </button>
      </form>

      {q && q.length < MIN_QUERY_LENGTH && <p className={styles.message}>Please enter at least {MIN_QUERY_LENGTH} characters.</p>}

      {q.length >= MIN_QUERY_LENGTH && (
        <p className={styles.message} role="status">
          {results.length === 0 ? `No results found for “${q}”.` : `${results.length} result${results.length === 1 ? '' : 's'}`}
        </p>
      )}

      {results.length > 0 && (
        <ol className={styles.results}>
          {results.map((r) => (
            <li key={r.href}>
              <Link href={r.href} className={styles.result}>
                <span className={styles.type}>{r.type}</span>
                <span className={styles.resultTitle}>{r.title}</span>
                {r.excerpt && <span className={styles.excerpt}>{r.excerpt}</span>}
              </Link>
            </li>
          ))}
        </ol>
      )}

      {(results.length === 0 || !q) && (
        <div className={styles.suggestions}>
          <h2 className={styles.suggestTitle}>Popular pages</h2>
          <ul>
            {SUGGESTIONS.map((s) => (
              <li key={s.href}>
                <Link href={s.href}>{s.label}</Link>
              </li>
            ))}
          </ul>
        </div>
      )}
    </section>
  )
}
