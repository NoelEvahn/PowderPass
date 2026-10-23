import type { LinkField, Page, Story } from '@/payload-types'
import { STORIES_BASE_PATH } from './routes'

export type ResolvedLink = { href: string; label: string; external: boolean; newTab: boolean }

type LinkLike = Partial<Pick<LinkField, 'type' | 'url' | 'label' | 'newTab' | 'reference'>> | null | undefined

const siteOrigin = (() => {
  try {
    return new URL(process.env.NEXT_PUBLIC_SERVER_URL || '').origin
  } catch {
    return null
  }
})()

/** Absolute http(s) URLs on another origin. mailto:, tel: and relative links are not "external". */
export const isExternalHref = (href: string) => {
  if (!/^https?:\/\//i.test(href)) return false
  try {
    return new URL(href).origin !== siteOrigin
  } catch {
    return false
  }
}

export const pageHref = (path: string | null | undefined) => (path ? `/${path}` : '/')

/** Turn a CMS link group into an href. Returns null when the target page is missing or unpublished. */
export function resolveLink(link: LinkLike): ResolvedLink | null {
  if (!link?.label) return null
  let href: string | null = null
  if (link.type === 'custom') {
    href = link.url?.trim() || null
  } else if (link.reference && typeof link.reference.value === 'object' && link.reference.value) {
    href =
      link.reference.relationTo === 'stories'
        ? `/${STORIES_BASE_PATH}/${(link.reference.value as Story).slug}`
        : pageHref((link.reference.value as Page).path)
  }
  if (!href) return null
  const external = isExternalHref(href)
  return { href, label: link.label, external, newTab: Boolean(link.newTab) || external }
}
