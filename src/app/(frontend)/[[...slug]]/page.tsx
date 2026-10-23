import type { Metadata } from 'next'
import { notFound } from 'next/navigation'
import { RenderBlocks } from '@/blocks/RenderBlocks'
import { Breadcrumbs } from '@/components/layout/Breadcrumbs'
import { LivePreviewListener } from '@/components/LivePreviewListener'
import { getBreadcrumbs, getPage, getSettings, isPreview } from '@/lib/queries'
import { breadcrumbJsonLd, buildMetadata, jsonLdString, schoolJsonLd } from '@/lib/seo'

type Props = { params: Promise<{ slug?: string[] }> }

// Pages render on first request and are cached until an editor publishes a change.
export const revalidate = 3600
export const dynamicParams = true

const toPath = (slug?: string[]) => (slug ?? []).map((part) => decodeURIComponent(part)).join('/')

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const path = toPath((await params).slug)
  const page = await getPage(path)
  if (!page) return { title: 'Page not found' }
  const firstHero = page.layout?.find((block) => block.blockType === 'hero')
  return buildMetadata({
    title: page.meta?.title || (path ? page.title : `${page.title}`),
    description: page.meta?.description || (firstHero && 'intro' in firstHero ? firstHero.intro : null),
    path: `/${path}`,
    image: page.meta?.image || (firstHero && 'media' in firstHero ? firstHero.media : null),
    noIndex: page.meta?.noIndex,
  })
}

export default async function CmsPage({ params }: Props) {
  const path = toPath((await params).slug)
  const [page, preview] = await Promise.all([getPage(path), isPreview()])
  if (!page) notFound()

  const isHome = path === ''
  const crumbs = isHome ? [] : await getBreadcrumbs(path)
  const current = page.navLabel || page.title
  const jsonLd = isHome
    ? schoolJsonLd(await getSettings())
    : breadcrumbJsonLd([{ label: 'Home', href: '/' }, ...crumbs, { label: current, href: `/${path}` }])

  return (
    <>
      {preview && <LivePreviewListener />}
      <script type="application/ld+json" dangerouslySetInnerHTML={{ __html: jsonLdString(jsonLd) }} />
      {!isHome && <Breadcrumbs items={crumbs} current={current} />}
      <RenderBlocks blocks={page.layout ?? []} />
    </>
  )
}
