'use client'
import { Plus, Search } from 'lucide-react'
import { type ReactNode, useEffect, useId, useState } from 'react'
import styles from './Faqs.module.css'

type Item = { id: string; question: string; searchText: string; answer: ReactNode }

/**
 * Accordion of real buttons with aria-expanded. Each question has a stable
 * anchor (#faq-<id>) so search results and links can open it directly.
 */
export function FaqList({ items, searchable }: { items: Item[]; searchable: boolean }) {
  const [open, setOpen] = useState<Set<string>>(new Set())
  const [filter, setFilter] = useState('')
  const filterId = useId()

  useEffect(() => {
    const openFromHash = () => {
      const match = window.location.hash.match(/^#faq-(.+)$/)
      if (match && items.some((i) => i.id === match[1])) {
        setOpen((prev) => new Set(prev).add(match[1]))
        requestAnimationFrame(() => document.getElementById(`faq-${match[1]}`)?.scrollIntoView({ block: 'start' }))
      }
    }
    openFromHash()
    window.addEventListener('hashchange', openFromHash)
    return () => window.removeEventListener('hashchange', openFromHash)
  }, [items])

  const terms = filter.trim().toLowerCase()
  const visible = terms ? items.filter((item) => terms.split(/\s+/).every((t) => item.searchText.includes(t))) : items

  const toggle = (id: string) =>
    setOpen((prev) => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })

  return (
    <div className={styles.wrap}>
      {searchable && (
        <div className={styles.filter}>
          <label htmlFor={filterId} className={styles.filterLabel}>
            Filter questions
          </label>
          <div className={styles.filterField}>
            <Search size={20} aria-hidden="true" />
            <input id={filterId} type="search" value={filter} onChange={(e) => setFilter(e.target.value)} placeholder="e.g. bus, uniform, visa" />
          </div>
          <p className={styles.filterStatus} role="status" aria-live="polite">
            {terms ? `${visible.length} of ${items.length} questions shown` : ''}
          </p>
        </div>
      )}
      {visible.length === 0 ? (
        <p className={styles.empty}>No questions match “{filter}”. Try another word, or contact the Admissions team.</p>
      ) : (
        <ul className={styles.list}>
          {visible.map((item) => {
            const isOpen = open.has(item.id)
            return (
              <li key={item.id} id={`faq-${item.id}`} className={styles.item}>
                <h3 className={styles.question}>
                  <button
                    type="button"
                    aria-expanded={isOpen}
                    aria-controls={`faq-panel-${item.id}`}
                    onClick={() => toggle(item.id)}
                    className={styles.button}
                  >
                    <span>{item.question}</span>
                    <Plus size={22} aria-hidden="true" className={styles.icon} />
                  </button>
                </h3>
                <div id={`faq-panel-${item.id}`} className={styles.answer} hidden={!isOpen}>
                  {item.answer}
                </div>
              </li>
            )
          })}
        </ul>
      )}
    </div>
  )
}
