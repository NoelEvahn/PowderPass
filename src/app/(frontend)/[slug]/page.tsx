import type { Metadata } from 'next'
import { draftMode } from 'next/headers'
import Link from 'next/link'
import { notFound } from 'next/navigation'
import React, { cache } from 'react'

import { RenderBlocks } from '@/components/blocks/RenderBlocks'
import { Icon } from '@/components/Icon'
import { getPayloadClient } from '@/lib/payload'

/** Slugs that have their own routes (or are system paths) and are never served from the Pages collection. */
const RESERVED = new Set([
  'shop',
  'cart',
  'checkout',
  'account',
  'order',
  'visit',
  'contact',
  'admin',
  'api',
  'next',
  'payments',
  'tasks',
])

const getPage = cache(async (slug: string, draft: boolean) => {
  if (RESERVED.has(slug)) return null
  const payload = await getPayloadClient()
  const { docs } = await payload.find({
    collection: 'pages',
    where: draft ? { slug: { equals: slug } } : { and: [{ slug: { equals: slug } }, { _status: { equals: 'published' } }] },
    depth: 1,
    limit: 1,
    draft,
  })
  return docs[0] ?? null
})

const formatDate = (iso: string) =>
  new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'long', year: 'numeric', timeZone: 'Asia/Kuala_Lumpur' }).format(
    new Date(iso),
  )

type Props = { params: Promise<{ slug: string }> }

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { slug } = await params
  const { isEnabled } = await draftMode()
  const page = await getPage(slug, isEnabled)
  if (!page) return { title: 'Page not found', robots: { index: false } }
  const description = page.seo?.description || page.intro || undefined
  return {
    title: page.title,
    description,
    alternates: { canonical: `/${slug}` },
    openGraph: { title: page.title, description, url: `/${slug}`, type: 'website' },
    robots: isEnabled ? { index: false, follow: false } : undefined,
  }
}

export default async function ContentPage({ params }: Props) {
  const { slug } = await params
  const { isEnabled } = await draftMode()
  const page = await getPage(slug, isEnabled)
  if (!page) notFound()

  const lang = slug === 'privasi' ? 'ms' : undefined
  return (
    <>
      <header className="page-hero">
        <div className="container">
          <nav className="breadcrumbs" aria-label="Breadcrumb">
            <ol>
              <li>
                <Link href="/">Home</Link>
              </li>
              <li aria-current="page">{page.title}</li>
            </ol>
          </nav>
          <h1 lang={lang}>{page.title}</h1>
          {page.intro ? (
            <p className="lead" lang={lang}>
              {page.intro}
            </p>
          ) : null}
          {page.lastUpdated ? (
            <p className="meta page-hero__updated">
              Last updated <time dateTime={page.lastUpdated.slice(0, 10)}>{formatDate(page.lastUpdated)}</time>
            </p>
          ) : null}
          {page.needsConfirmation ? (
            <div className="notice notice--pending page-hero__notice">
              <Icon name="clock" />
              <p>Parts of this page are awaiting confirmation by Label Trunc. Items in [square brackets] are not final.</p>
            </div>
          ) : null}
        </div>
      </header>
      <div lang={lang}>
        <RenderBlocks blocks={page.layout} />
      </div>
    </>
  )
}
