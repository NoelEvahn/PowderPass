import type { Metadata } from 'next'
import type { Media, SiteSetting } from '@/payload-types'

export const siteUrl = () => (process.env.NEXT_PUBLIC_SERVER_URL || 'http://localhost:3000').replace(/\/$/, '')

type MetaInput = {
  title: string
  description?: string | null
  path: string
  image?: Media | number | null
  noIndex?: boolean | null
  type?: 'website' | 'article'
  publishedTime?: string | null
}

export function buildMetadata({ title, description, path, image, noIndex, type = 'website', publishedTime }: MetaInput): Metadata {
  const url = `${siteUrl()}${path === '/' ? '' : path}` || siteUrl()
  const media = image && typeof image === 'object' ? image : null
  const ogImage = media?.sizes?.og?.url ?? media?.url
  return {
    title,
    description: description ?? undefined,
    alternates: { canonical: url },
    robots: noIndex ? { index: false, follow: true } : undefined,
    openGraph: {
      title,
      description: description ?? undefined,
      url,
      type,
      ...(publishedTime ? { publishedTime } : {}),
      ...(ogImage ? { images: [{ url: ogImage, width: 1200, height: 630, alt: media?.alt ?? '' }] } : {}),
    },
  }
}

/** schema.org data for the school, using only verified fields from School details. */
export function schoolJsonLd(settings: SiteSetting) {
  const main = settings.campuses?.find((c) => c.key === 'main') ?? settings.campuses?.[0]
  return {
    '@context': 'https://schema.org',
    '@type': 'School',
    name: settings.schoolName,
    alternateName: settings.shortName,
    url: siteUrl(),
    logo: `${siteUrl()}/brand/isp-logo.svg`,
    email: settings.generalEmail,
    ...(main?.phones?.[0] ? { telephone: main.phones[0].number } : {}),
    ...(main
      ? {
          address: {
            '@type': 'PostalAddress',
            streetAddress: main.address.split('\n')[0]?.replace(/,$/, ''),
            addressLocality: 'Kuala Lumpur',
            postalCode: main.address.match(/\b\d{5}\b/)?.[0],
            addressCountry: 'MY',
          },
        }
      : {}),
    sameAs: (settings.social ?? []).map((s) => s.url),
  }
}

export function breadcrumbJsonLd(items: { label: string; href: string }[]) {
  return {
    '@context': 'https://schema.org',
    '@type': 'BreadcrumbList',
    itemListElement: items.map((item, index) => ({
      '@type': 'ListItem',
      position: index + 1,
      name: item.label,
      item: `${siteUrl()}${item.href}`,
    })),
  }
}

/** Safe JSON for a <script type="application/ld+json"> tag. */
export const jsonLdString = (data: unknown) => JSON.stringify(data).replace(/</g, '\\u003c')
