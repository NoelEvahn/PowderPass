import 'server-only'

import { draftMode } from 'next/headers'
import { cache } from 'react'

import { getPayloadClient } from '@/lib/payload'
import type { HomePage, SiteSetting } from '@/payload-types'

/** Contact and business details for this request. */
export const getSite = cache(async (): Promise<SiteSetting> => {
  const payload = await getPayloadClient()
  return payload.findGlobal({ slug: 'site-settings', depth: 0 })
})

export const getHome = cache(async (): Promise<HomePage> => {
  const payload = await getPayloadClient()
  const { isEnabled } = await draftMode()
  return payload.findGlobal({ slug: 'home-page', depth: 1, draft: isEnabled })
})

export const addressLines = (site: SiteSetting): string[] => {
  const a = site.address
  if (!a) return []
  return [a.line1, a.line2, [a.postcode, a.city].filter(Boolean).join(' '), [a.state, a.country].filter(Boolean).join(', ')].filter(
    (l): l is string => Boolean(l && l.trim()),
  )
}

export const whatsappLink = (site: Pick<SiteSetting, 'whatsapp'>, text?: string) =>
  `https://wa.me/${site.whatsapp}${text ? `?text=${encodeURIComponent(text)}` : ''}`

export const telLink = (phone: string) => `tel:${phone.replace(/[^\d+]/g, '')}`

export const announcementActive = (site: SiteSetting) => {
  const a = site.announcement
  if (!a?.enabled || !a.text) return false
  return !a.expiresAt || new Date(a.expiresAt).getTime() > Date.now()
}
