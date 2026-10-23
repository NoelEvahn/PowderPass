import 'server-only'
import { draftMode } from 'next/headers'
import { unstable_cache } from 'next/cache'
import type { Where } from 'payload'
import { cache } from 'react'
import { SITE_TAG } from '@/hooks/revalidate'
import type { Admission, Event, Footer, Header, Page, SiteSetting, Story } from '@/payload-types'
import { getPayloadClient } from './payload'

/** Published content is cached and invalidated on publish; drafts (preview) are always fresh. */
const cached = <A extends unknown[], R>(key: string, fn: (...args: A) => Promise<R>) =>
  unstable_cache(fn, [key], { tags: [SITE_TAG], revalidate: 3600 })

export const isPreview = cache(async () => (await draftMode()).isEnabled)

const fetchPage = async (path: string, draft: boolean): Promise<Page | null> => {
  const payload = await getPayloadClient()
  const res = await payload.find({
    collection: 'pages',
    // The home page has an empty path, which the query layer ignores; match it by slug instead.
    where: path ? { path: { equals: path } } : { and: [{ slug: { equals: 'home' } }, { parent: { exists: false } }] },
    depth: 2,
    limit: 1,
    draft,
    overrideAccess: draft,
  })
  return res.docs[0] ?? null
}
const cachedPage = cached('page', (path: string) => fetchPage(path, false))

export const getPage = cache(async (path: string) => ((await isPreview()) ? fetchPage(path, true) : cachedPage(path)))

/** Titles and paths of ancestor pages, for breadcrumbs. */
export const getBreadcrumbs = cache(async (path: string) => {
  const parts = path.split('/').filter(Boolean)
  const prefixes = parts.slice(0, -1).map((_, i) => parts.slice(0, i + 1).join('/'))
  if (!prefixes.length) return []
  const load = cached('crumbs', async (keys: string) => {
    const payload = await getPayloadClient()
    const res = await payload.find({
      collection: 'pages',
      where: { and: [{ path: { in: keys.split('|') } }, { _status: { equals: 'published' } }] },
      depth: 0,
      limit: 10,
      select: { title: true, navLabel: true, path: true },
    })
    return res.docs
  })
  const docs = await load(prefixes.join('|'))
  return prefixes.flatMap((p) => {
    const doc = docs.find((d) => d.path === p)
    return doc ? [{ label: doc.navLabel || doc.title, href: `/${p}` }] : []
  })
})

const fetchGlobal = async <T,>(slug: 'header' | 'footer' | 'site-settings' | 'admissions', draft = false): Promise<T> => {
  const payload = await getPayloadClient()
  return (await payload.findGlobal({ slug, depth: 1, draft, overrideAccess: draft })) as T
}

export const getHeader = cache(() => cached('header', () => fetchGlobal<Header>('header'))())
export const getFooter = cache(() => cached('footer', () => fetchGlobal<Footer>('footer'))())
export const getSettings = cache(() => cached('settings', () => fetchGlobal<SiteSetting>('site-settings'))())
export const getAdmissions = cache(async () =>
  (await isPreview()) ? fetchGlobal<Admission>('admissions', true) : cached('admissions', () => fetchGlobal<Admission>('admissions'))(),
)

export const getProgrammes = cache(
  cached('programmes', async () => {
    const payload = await getPayloadClient()
    const res = await payload.find({ collection: 'programmes', where: { _status: { equals: 'published' } }, depth: 2, sort: 'order', limit: 10 })
    return res.docs
  }),
)

export const getStories = cache(async ({ category, limit }: { category?: string; limit: number }) => {
  const load = cached('stories', async (cat: string, max: number) => {
    const payload = await getPayloadClient()
    const where: Where = { _status: { equals: 'published' } }
    const res = await payload.find({
      collection: 'stories',
      where: cat && cat !== 'all' ? { and: [where, { category: { equals: cat } }] } : where,
      depth: 1,
      limit: max,
      sort: '-publishedAt',
      select: { title: true, slug: true, category: true, publishedAt: true, excerpt: true, heroImage: true },
    })
    return res.docs as Story[]
  })
  return load(category ?? 'all', limit)
})

const fetchStory = async (slug: string, draft: boolean) => {
  const payload = await getPayloadClient()
  const res = await payload.find({ collection: 'stories', where: { slug: { equals: slug } }, depth: 2, limit: 1, draft, overrideAccess: draft })
  return res.docs[0] ?? null
}
const cachedStory = cached('story', (slug: string) => fetchStory(slug, false))
export const getStory = cache(async (slug: string) => ((await isPreview()) ? fetchStory(slug, true) : cachedStory(slug)))

/**
 * Upcoming events. Events whose end time has passed are excluded, which is the
 * automatic archive rule. The cache is short so events drop off promptly.
 */
export const getUpcomingEvents = cache(async ({ campus, limit }: { campus?: string; limit: number }) => {
  const load = unstable_cache(
    async (camp: string, max: number) => {
      const payload = await getPayloadClient()
      const conditions: Where[] = [{ _status: { equals: 'published' } }, { endsAt: { greater_than: new Date().toISOString() } }]
      if (camp && camp !== 'all') conditions.push({ campus: { equals: camp } })
      const res = await payload.find({ collection: 'events', where: { and: conditions }, depth: 0, limit: max, sort: 'startsAt' })
      return res.docs as Event[]
    },
    ['events'],
    { tags: [SITE_TAG], revalidate: 300 },
  )
  return load(campus ?? 'all', limit)
})

export const getFaqs = cache(async (categories: string[] | null | undefined) => {
  const load = cached('faqs', async (cats: string) => {
    const payload = await getPayloadClient()
    const conditions: Where[] = [{ _status: { equals: 'published' } }]
    if (cats) conditions.push({ category: { in: cats.split('|') } })
    const res = await payload.find({ collection: 'faqs', where: { and: conditions }, depth: 0, limit: 100, sort: 'order' })
    return res.docs
  })
  return load((categories ?? []).join('|'))
})
