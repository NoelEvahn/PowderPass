import type { Endpoint } from 'payload'
import { rateLimit } from '@/lib/rate-limit'
import { searchSite } from '@/lib/search'
import { clientIp } from '@/lib/request'

/** GET /api/site-search?q=term — used by the header search dialog. */
export const searchEndpoint: Endpoint = {
  path: '/site-search',
  method: 'get',
  handler: async (req) => {
    const ip = clientIp(req.headers)
    if (!rateLimit(`search:${ip}`, { limit: 60, windowMs: 60_000 }).ok) {
      return Response.json({ error: 'Too many searches. Please wait a moment.' }, { status: 429 })
    }
    const q = typeof req.query?.q === 'string' ? req.query.q : ''
    try {
      const results = await searchSite(req.payload, q, 8)
      return Response.json({ results }, { headers: { 'Cache-Control': 'public, max-age=60' } })
    } catch (error) {
      req.payload.logger.error({ err: error, msg: 'Site search failed' })
      return Response.json({ error: 'Search is temporarily unavailable.' }, { status: 500 })
    }
  },
}
