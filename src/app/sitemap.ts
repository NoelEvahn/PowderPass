import type { MetadataRoute } from 'next'
import { getPayloadClient } from '@/lib/payload'
import { STORIES_BASE_PATH } from '@/lib/routes'
import { siteUrl } from '@/lib/seo'

export const revalidate = 3600

export default async function sitemap(): Promise<MetadataRoute.Sitemap> {
  const payload = await getPayloadClient()
  const published = { _status: { equals: 'published' } } as const
  const [pages, stories] = await Promise.all([
    payload.find({ collection: 'pages', where: { and: [published, { 'meta.noIndex': { not_equals: true } }] }, depth: 0, limit: 1000, select: { path: true, updatedAt: true } }),
    payload.find({ collection: 'stories', where: { and: [published, { 'meta.noIndex': { not_equals: true } }] }, depth: 0, limit: 1000, select: { slug: true, updatedAt: true } }),
  ])
  const base = siteUrl()
  return [
    ...pages.docs.map((page) => ({
      url: page.path ? `${base}/${page.path}` : base,
      lastModified: page.updatedAt,
      priority: page.path ? (page.path.split('/').length === 1 ? 0.8 : 0.6) : 1,
    })),
    ...stories.docs.map((story) => ({ url: `${base}/${STORIES_BASE_PATH}/${story.slug}`, lastModified: story.updatedAt, priority: 0.5 })),
  ]
}
