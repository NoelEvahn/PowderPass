'use client'

import React, { useRef, useState } from 'react'

import { Icon } from '@/components/Icon'
import { MediaImage } from '@/components/MediaImage'
import type { Media } from '@/payload-types'

/**
 * Product photos: one main image in a fixed 4:5 frame (so nothing moves when the
 * photo changes) and a row of thumbnails. Every photo is in the page; arrow keys
 * move between thumbnails, and phones can swipe the main photo.
 */
export const Gallery = ({ images, title }: { images: Media[]; title: string }) => {
  const [index, setIndex] = useState(0)
  const thumbs = useRef<(HTMLButtonElement | null)[]>([])
  const touchX = useRef<number | null>(null)
  const count = images.length

  const go = (next: number, focus = false) => {
    const i = (next + count) % count
    setIndex(i)
    if (focus) thumbs.current[i]?.focus()
  }

  const onKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'ArrowRight' || e.key === 'ArrowDown') {
      e.preventDefault()
      go(index + 1, true)
    } else if (e.key === 'ArrowLeft' || e.key === 'ArrowUp') {
      e.preventDefault()
      go(index - 1, true)
    } else if (e.key === 'Home') {
      e.preventDefault()
      go(0, true)
    } else if (e.key === 'End') {
      e.preventDefault()
      go(count - 1, true)
    }
  }

  if (!count) {
    return (
      <div className="gallery">
        <div className="gallery__frame gallery__frame--empty">
          <Icon name="bag" size={48} />
          <span className="visually-hidden">No photo yet</span>
        </div>
      </div>
    )
  }

  return (
    <div className="gallery" role="group" aria-roledescription="gallery" aria-label={`Photos of ${title}`}>
      <div
        className="gallery__frame"
        onTouchStart={(e) => {
          touchX.current = e.touches[0]?.clientX ?? null
        }}
        onTouchEnd={(e) => {
          const start = touchX.current
          touchX.current = null
          const end = e.changedTouches[0]?.clientX
          if (start == null || end == null || count < 2) return
          if (Math.abs(end - start) > 50) go(index + (end < start ? 1 : -1))
        }}
      >
        {images.map((m, i) => (
          <div key={m.id} className="gallery__slide" hidden={i !== index} id={`gallery-photo-${i}`}>
            <MediaImage
              media={m}
              priority={i === 0}
              sizes="(min-width: 960px) 52vw, 100vw"
              alt={m.alt ? m.alt : `${title}, photo ${i + 1} of ${count}`}
            />
          </div>
        ))}
        {count > 1 ? (
          <>
            <button type="button" className="gallery__nav gallery__nav--prev" onClick={() => go(index - 1)} aria-label="Previous photo">
              <Icon name="arrow-left" />
            </button>
            <button type="button" className="gallery__nav gallery__nav--next" onClick={() => go(index + 1)} aria-label="Next photo">
              <Icon name="arrow" />
            </button>
          </>
        ) : null}
      </div>
      <p className="gallery__count meta" aria-live="polite">
        {count > 1 ? `${index + 1} of ${count}` : ' '}
      </p>
      {count > 1 ? (
        <ul className="gallery__thumbs" onKeyDown={onKeyDown}>
          {images.map((m, i) => (
            <li key={m.id}>
              <button
                type="button"
                ref={(el) => {
                  thumbs.current[i] = el
                }}
                className="gallery__thumb"
                aria-label={`Show photo ${i + 1} of ${count}`}
                aria-controls={`gallery-photo-${i}`}
                aria-current={i === index ? 'true' : undefined}
                tabIndex={i === index ? 0 : -1}
                onClick={() => setIndex(i)}
              >
                <MediaImage media={m} alt="" sizes="96px" />
              </button>
            </li>
          ))}
        </ul>
      ) : null}
    </div>
  )
}
