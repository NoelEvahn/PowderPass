import { NextResponse } from 'next/server'
import { timingSafeEqual } from 'crypto'

import { releaseExpired } from '@/lib/commerce/cart'
import { getPayloadClient } from '@/lib/payload'

export const dynamic = 'force-dynamic'

const authorised = (request: Request) => {
  const secret = process.env.CRON_SECRET
  if (!secret) return false
  const given = request.headers.get('authorization')?.replace(/^Bearer\s+/i, '') ?? ''
  return given.length === secret.length && timingSafeEqual(Buffer.from(given), Buffer.from(secret))
}

/**
 * Ends holds that have run out. Holds also end on their own whenever the shop is
 * visited; call this every few minutes from a scheduler (see README) so expiry
 * emails go out on time even when the site is quiet.
 */
async function handle(request: Request) {
  if (!authorised(request)) return NextResponse.json({ error: 'Unauthorised' }, { status: 401 })
  const payload = await getPayloadClient()
  await releaseExpired(payload, { force: true })
  return NextResponse.json({ ok: true })
}

export const GET = handle
export const POST = handle
