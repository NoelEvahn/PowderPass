'use client'
import { ChevronLeft, ChevronRight, Expand } from 'lucide-react'
import { useCallback, useEffect, useState } from 'react'
import { Dialog } from '@/components/ui/Dialog'
import { Media } from '@/components/ui/Media'
import type { Media as MediaDoc } from '@/payload-types'
import styles from './Gallery.module.css'

type Item = { id: string; image: MediaDoc; caption: string }

/** Thumbnail grid; every image opens an accessible full-screen viewer with previous/next and arrow keys. */
export function GalleryGrid({ items, layout, label }: { items: Item[]; layout: string; label: string }) {
  const [openIndex, setOpenIndex] = useState<number | null>(null)
  const count = items.length

  const step = useCallback((delta: number) => setOpenIndex((i) => (i === null ? i : (i + delta + count) % count)), [count])

  useEffect(() => {
    if (openIndex === null) return
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'ArrowRight') step(1)
      if (event.key === 'ArrowLeft') step(-1)
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [openIndex, step])

  const current = openIndex !== null ? items[openIndex] : null

  return (
    <>
      <ul className={styles.grid} data-layout={layout}>
        {items.map((item, index) => (
          <li key={item.id} className={styles.item} data-reveal style={{ '--reveal-index': index % 4 } as React.CSSProperties}>
            <button type="button" className={styles.thumb} onClick={() => setOpenIndex(index)} aria-haspopup="dialog">
              <Media
                media={item.image}
                maxSize={layout === 'posters' ? 'card' : 'feature'}
                sizes="(min-width: 1024px) 33vw, (min-width: 640px) 50vw, 100vw"
                className={styles.image}
              />
              <span className={styles.expand} aria-hidden="true">
                <Expand size={18} />
              </span>
              <span className="visually-hidden">View larger: {item.caption || item.image.alt}</span>
            </button>
            {item.caption && layout !== 'posters' && <p className={styles.caption}>{item.caption}</p>}
          </li>
        ))}
      </ul>

      <Dialog
        open={current !== null}
        onClose={() => setOpenIndex(null)}
        title={current ? `${label}: image ${(openIndex ?? 0) + 1} of ${count}` : label}
        variant="media"
      >
        {current && (
          <div className={styles.viewer}>
            <figure className={styles.figure}>
              <Media media={current.image} maxSize="hero" sizes="100vw" className={styles.full} priority />
              {(current.caption || current.image.alt) && <figcaption className={styles.figcaption}>{current.caption || current.image.alt}</figcaption>}
            </figure>
            {count > 1 && (
              <div className={styles.nav}>
                <button type="button" onClick={() => step(-1)} className={styles.navButton}>
                  <ChevronLeft size={22} aria-hidden="true" />
                  <span className="visually-hidden">Previous image</span>
                </button>
                <span className={styles.counter} aria-live="polite">
                  {(openIndex ?? 0) + 1} / {count}
                </span>
                <button type="button" onClick={() => step(1)} className={styles.navButton}>
                  <ChevronRight size={22} aria-hidden="true" />
                  <span className="visually-hidden">Next image</span>
                </button>
              </div>
            )}
          </div>
        )}
      </Dialog>
    </>
  )
}
