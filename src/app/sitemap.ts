import type { MetadataRoute } from 'next'

import { getServerURL } from '@/lib/env'
import { getPayloadClient } from '@/lib/payload'

// The database is not available at build time, so this is built per request.
export const dynamic = 'force-dynamic'

export default async function sitemap(): Promise<MetadataRoute.Sitemap> {
  const base = getServerURL()
  const entries: MetadataRoute.Sitemap = ['', '/shop', '/visit', '/contact'].map((path) => ({
    url: `${base}${path || '/'}`,
    changeFrequency: path === '' || path === '/shop' ? 'weekly' : 'monthly',
    priority: path === '' ? 1 : path === '/shop' ? 0.9 : 0.6,
  }))

  try {
    const payload = await getPayloadClient()
    const [pages, products] = await Promise.all([
      payload.find({
        collection: 'pages',
        where: { _status: { equals: 'published' } },
        limit: 1000,
        pagination: false,
        depth: 0,
        select: { slug: true, updatedAt: true },
        draft: false,
      }),
      payload.find({
        collection: 'products',
        where: { and: [{ _status: { equals: 'published' } }, { status: { not_equals: 'archived' } }] },
        limit: 5000,
        pagination: false,
        depth: 0,
        select: { slug: true, updatedAt: true },
        draft: false,
      }),
    ])
    for (const page of pages.docs) {
      if (!page.slug) continue
      entries.push({ url: `${base}/${page.slug}`, lastModified: page.updatedAt, changeFrequency: 'monthly', priority: 0.5 })
    }
    for (const product of products.docs) {
      if (!product.slug) continue
      entries.push({ url: `${base}/shop/${product.slug}`, lastModified: product.updatedAt, changeFrequency: 'weekly', priority: 0.7 })
    }
  } catch {
    // If the database is unreachable, still serve the fixed pages.
  }
  return entries
}
