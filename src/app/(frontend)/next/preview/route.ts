import config from '@payload-config'
import { draftMode, headers } from 'next/headers'
import { redirect } from 'next/navigation'
import { getPayload } from 'payload'

/**
 * Enables draft mode for a signed-in editor, then shows the requested page.
 * Used by the CMS Preview button and Live Preview iframe. Anyone without a
 * valid editor session is refused, so drafts are never exposed publicly.
 */
export async function GET(request: Request) {
  const url = new URL(request.url)
  const path = url.searchParams.get('path') || '/'
  // Only allow same-site relative paths (prevents open redirects).
  if (!path.startsWith('/') || path.startsWith('//')) return new Response('Invalid path', { status: 400 })

  const payload = await getPayload({ config })
  const { user } = await payload.auth({ headers: await headers() })
  if (!user) return new Response('Sign in to the website editor to preview drafts.', { status: 403 })

  ;(await draftMode()).enable()
  redirect(path)
}
