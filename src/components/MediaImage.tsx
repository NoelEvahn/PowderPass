import React from 'react'

import type { Media } from '@/payload-types'

type Props = {
  media: Media | number | null | undefined
  /** The `sizes` attribute: how wide the image shows at each breakpoint. */
  sizes: string
  className?: string
  priority?: boolean
  /** Override alt text (e.g. empty for decorative duplicates). */
  alt?: string
}

const SIZE_ORDER = ['thumb', 'card', 'large', 'xlarge'] as const

/**
 * Responsive image from the media library using the web sizes Payload generates
 * (400/800/1400/2200, WebP). Width and height are always set so nothing jumps.
 */
export const MediaImage = ({ media, sizes, className, priority = false, alt }: Props) => {
  if (!media || typeof media !== 'object' || !media.url) return null
  const candidates = SIZE_ORDER.map((key) => media.sizes?.[key]).filter(
    (s): s is { url: string; width: number; height: number } => Boolean(s?.url && s.width && s.height),
  )
  if (media.width && media.url && !candidates.some((c) => c.width === media.width)) {
    candidates.push({ url: media.url, width: media.width, height: media.height ?? media.width })
  }
  const srcSet = candidates.map((c) => `${c.url} ${c.width}w`).join(', ')
  const fallback = candidates.find((c) => c.width >= 800) ?? candidates[candidates.length - 1]
  const focal =
    typeof media.focalX === 'number' && typeof media.focalY === 'number' ? `${media.focalX}% ${media.focalY}%` : undefined

  return (
    // eslint-disable-next-line @next/next/no-img-element
    <img
      src={fallback?.url ?? media.url}
      srcSet={srcSet || undefined}
      sizes={sizes}
      width={media.width ?? fallback?.width ?? undefined}
      height={media.height ?? fallback?.height ?? undefined}
      alt={alt ?? media.alt ?? ''}
      className={className}
      loading={priority ? 'eager' : 'lazy'}
      decoding="async"
      fetchPriority={priority ? 'high' : undefined}
      style={focal ? { objectPosition: focal } : undefined}
    />
  )
}
