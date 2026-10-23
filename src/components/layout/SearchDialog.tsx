'use client'
import { ArrowRight, Loader2, Search } from 'lucide-react'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { useEffect, useId, useRef, useState } from 'react'
import { Dialog } from '../ui/Dialog'
import styles from './SearchDialog.module.css'

type Result = { type: string; title: string; href: string; excerpt: string }

const SHORTCUTS = [
  { label: 'Early Years (ages 3–5)', href: '/learning/early-years' },
  { label: 'Primary (ages 5–11)', href: '/learning/primary' },
  { label: 'Secondary (ages 11–16)', href: '/learning/secondary' },
  { label: 'Sixth Form (ages 16–18)', href: '/learning/sixth-form' },
  { label: 'Fees', href: '/admissions/fees' },
  { label: 'Visit & Enquire', href: '/admissions/visit' },
  { label: 'How to apply', href: '/admissions/apply' },
  { label: 'Parent questions', href: '/admissions/faqs' },
]

type State = { status: 'idle' | 'loading' | 'done' | 'error'; results: Result[]; message?: string }

export function SearchDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  return (
    <Dialog open={open} onClose={onClose} title="Search" hideTitle variant="sheet">
      <SearchPanel onNavigate={onClose} />
    </Dialog>
  )
}

/** Live search with keyboard navigation (arrow keys + Enter) over the results list. */
function SearchPanel({ onNavigate }: { onNavigate: () => void }) {
  const router = useRouter()
  const inputRef = useRef<HTMLInputElement>(null)
  const listId = useId()
  const [query, setQuery] = useState('')
  const [state, setState] = useState<State>({ status: 'idle', results: [] })
  const [active, setActive] = useState(-1)

  useEffect(() => inputRef.current?.focus(), [])

  useEffect(() => {
    const q = query.trim()
    if (q.length < 2) return
    const controller = new AbortController()
    const timer = setTimeout(async () => {
      setState((s) => ({ ...s, status: 'loading' }))
      try {
        const res = await fetch(`/api/site-search?q=${encodeURIComponent(q)}`, { signal: controller.signal })
        const body = await res.json()
        if (!res.ok) throw new Error(body?.error || 'Search failed')
        setState({ status: 'done', results: body.results })
        setActive(-1)
      } catch (error) {
        if ((error as Error).name !== 'AbortError') setState({ status: 'error', results: [], message: (error as Error).message })
      }
    }, 220)
    return () => {
      clearTimeout(timer)
      controller.abort()
    }
  }, [query])

  const go = (href: string) => {
    onNavigate()
    router.push(href)
  }

  const onKeyDown = (event: React.KeyboardEvent) => {
    const results = query.trim().length < 2 ? [] : state.results
    const count = results.length
    if (event.key === 'ArrowDown' && count) {
      event.preventDefault()
      setActive((i) => (i + 1) % count)
    } else if (event.key === 'ArrowUp' && count) {
      event.preventDefault()
      setActive((i) => (i <= 0 ? count - 1 : i - 1))
    } else if (event.key === 'Enter' && active >= 0 && results[active]) {
      event.preventDefault()
      go(results[active].href)
    }
  }

  const q = query.trim()
  // Short queries always show the shortcuts, whatever the last response was.
  const view: State = q.length < 2 ? { status: 'idle', results: [] } : state

  return (
    <div className={styles.panel}>
      <form
        role="search"
        className={styles.form}
        action="/search"
        onSubmit={(event) => {
          event.preventDefault()
          if (q.length >= 2) go(`/search?q=${encodeURIComponent(q)}`)
        }}
      >
        <label htmlFor={`${listId}-input`} className="visually-hidden">
          Search the website
        </label>
        <Search size={22} aria-hidden="true" className={styles.icon} />
        <input
          ref={inputRef}
          id={`${listId}-input`}
          name="q"
          type="search"
          autoComplete="off"
          placeholder="Search fees, Sixth Form subjects, bus service…"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          onKeyDown={onKeyDown}
          role="combobox"
          aria-expanded={view.results.length > 0}
          aria-controls={listId}
          aria-activedescendant={active >= 0 ? `${listId}-opt-${active}` : undefined}
          className={styles.input}
        />
        {view.status === 'loading' && <Loader2 size={20} className={styles.spinner} aria-hidden="true" />}
      </form>

      <p className="visually-hidden" role="status" aria-live="polite">
        {view.status === 'done' ? `${view.results.length} results` : view.status === 'error' ? view.message : ''}
      </p>

      {q.length < 2 && (
        <div className={styles.shortcuts}>
          <p className={styles.label}>Popular</p>
          <ul>
            {SHORTCUTS.map((item) => (
              <li key={item.href}>
                <Link href={item.href} onClick={onNavigate} className={styles.chip}>
                  {item.label}
                </Link>
              </li>
            ))}
          </ul>
        </div>
      )}

      {view.status === 'error' && <p className={styles.message}>{view.message} Try again, or use the menu.</p>}

      {view.status === 'done' && view.results.length === 0 && (
        <div className={styles.message}>
          <p>
            No results for “{q}”. Try a broader word such as “fees”, “Primary” or “visit”, or{' '}
            <Link href="/contact" onClick={onNavigate}>
              contact the school
            </Link>
            .
          </p>
        </div>
      )}

      {view.results.length > 0 && (
        <>
          <ul id={listId} role="listbox" aria-label="Search results" className={styles.results}>
            {view.results.map((result, index) => (
              <li key={result.href} id={`${listId}-opt-${index}`} role="option" aria-selected={index === active}>
                <Link href={result.href} onClick={onNavigate} className={styles.result} tabIndex={-1} data-active={index === active || undefined}>
                  <span className={styles.type}>{result.type}</span>
                  <span className={styles.title}>{result.title}</span>
                  {result.excerpt && <span className={styles.excerpt}>{result.excerpt}</span>}
                </Link>
              </li>
            ))}
          </ul>
          <Link href={`/search?q=${encodeURIComponent(q)}`} onClick={onNavigate} className={styles.all}>
            See all results <ArrowRight size={18} aria-hidden="true" />
          </Link>
        </>
      )}
    </div>
  )
}
