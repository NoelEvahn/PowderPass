import { resolveLink } from '@/lib/links'
import { getHeader, getSettings } from '@/lib/queries'
import { HeaderClient } from './HeaderClient'
import type { NavGroup } from './nav-types'
import { AnnouncementBar } from './AnnouncementBar'

export async function SiteHeader() {
  const [header, settings] = await Promise.all([getHeader(), getSettings()])

  const groups: NavGroup[] = (header.navGroups ?? []).map((group, index) => ({
    id: group.id ?? `group-${index}`,
    label: group.label,
    summary: group.summary,
    overview: resolveLink(group.overview),
    items: (group.items ?? []).flatMap((item) => {
      const link = resolveLink(item.link)
      return link ? [{ ...link, description: item.description }] : []
    }),
  }))
  const utility = (header.utilityLinks ?? []).flatMap((item) => {
    const link = resolveLink(item.link)
    return link ? [link] : []
  })
  const cta = resolveLink(header.cta)
  const sister = settings.sisterSchool?.url && settings.sisterSchool.label
    ? { href: settings.sisterSchool.url, label: settings.sisterSchool.label, external: true, newTab: true }
    : null

  return (
    <>
      <AnnouncementBar announcement={settings.announcement} />
      <HeaderClient groups={groups} utility={sister ? [...utility, sister] : utility} cta={cta} schoolName={settings.schoolName} />
    </>
  )
}
