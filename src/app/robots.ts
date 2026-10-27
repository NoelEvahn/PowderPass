import type { MetadataRoute } from 'next'

import { getServerURL } from '@/lib/env'

export default function robots(): MetadataRoute.Robots {
  const base = getServerURL()
  return {
    rules: [{ userAgent: '*', allow: '/', disallow: ['/admin', '/account', '/cart', '/checkout', '/order', '/next', '/api'] }],
    sitemap: `${base}/sitemap.xml`,
  }
}
