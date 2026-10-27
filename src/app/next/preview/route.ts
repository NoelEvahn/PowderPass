import { draftMode, headers } from 'next/headers'
import { redirect } from 'next/navigation'

import { getPayloadClient } from '@/lib/payload'

export const dynamic = 'force-dynamic'

/** Staff-only: turn on draft mode and open the page, so unpublished changes show. */
export async function GET(request: Request) {
  const url = new URL(request.url)
  const path = url.searchParams.get('path') || '/'
  if (!path.startsWith('/') || path.startsWith('//')) return new Response('Invalid path', { status: 400 })

  const payload = await getPayloadClient()
  const { user } = await payload.auth({ headers: await headers() })
  if (!user || user.collection !== 'users') {
    return new Response('Sign in to the editor to preview drafts.', { status: 403 })
  }
  ;(await draftMode()).enable()
  redirect(path)
}
