/* eslint-disable @next/next/no-img-element -- Payload generates responsive WebP sizes; next/image would re-encode them. */
import type { Media as MediaDoc } from '@/payload-types'

type SizeName = 'thumbnail' | 'card' | 'feature' | 'hero'

type Props = {
  media: MediaDoc | number | null | undefined
  /** CSS `sizes` hint, e.g. "(min-width: 1024px) 50vw, 100vw". */
  sizes?: string
  /** Largest size to include in srcset. */
  maxSize?: SizeName
  priority?: boolean
  className?: string
  alt?: string
  fill?: boolean
}

const ORDER: SizeName[] = ['thumbnail', 'card', 'feature', 'hero']

/**
 * Responsive image from the media library. Uses focal point for cropping,
 * explicit dimensions to prevent layout shift, and lazy loading below the fold.
 */
export function Media({ media, sizes = '100vw', maxSize = 'feature', priority, className, alt, fill }: Props) {
  if (!media || typeof media !== 'object' || !media.url) return null
  const isImage = media.mimeType?.startsWith('image/')
  if (!isImage) return null

  const candidates = ORDER.slice(0, ORDER.indexOf(maxSize) + 1)
    .map((name) => media.sizes?.[name])
    .filter((s): s is NonNullable<typeof s> => Boolean(s?.url && s.width))
  const srcSet = candidates.map((s) => `${s.url} ${s.width}w`).join(', ')
  const fallback = candidates.at(-1)?.url ?? media.url
  const objectPosition = `${media.focalX ?? 50}% ${media.focalY ?? 50}%`
  const decorative = media.alt?.trim().toLowerCase() === 'decorative'

  return (
    <img
      src={fallback}
      srcSet={srcSet || undefined}
      sizes={srcSet ? sizes : undefined}
      width={media.width ?? undefined}
      height={media.height ?? undefined}
      alt={alt ?? (decorative ? '' : media.alt)}
      loading={priority ? 'eager' : 'lazy'}
      decoding={priority ? 'sync' : 'async'}
      fetchPriority={priority ? 'high' : undefined}
      className={className}
      style={{ objectPosition, ...(fill ? { width: '100%', height: '100%', objectFit: 'cover' } : null) }}
    />
  )
}
