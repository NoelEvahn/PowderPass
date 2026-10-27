import type { Metadata, Viewport } from 'next'
import { Inter, Tinos } from 'next/font/google'
import { draftMode } from 'next/headers'
import Link from 'next/link'
import React from 'react'

import { Footer } from '@/components/site/Footer'
import { Header } from '@/components/site/Header'
import { RevealObserver } from '@/components/site/RevealObserver'
import { getServerURL } from '@/lib/env'
import { getSite } from '@/lib/site'

import '@/styles/tokens.css'
import '@/styles/base.css'
import '@/styles/site.css'
import '@/styles/content.css'
import '@/styles/shop.css'
import '@/styles/home.css'

// Fonts are downloaded at build time and served from this site (no runtime font requests).
const display = Tinos({ subsets: ['latin'], weight: ['400', '700'], style: ['normal', 'italic'], variable: '--font-display', display: 'swap' })
const body = Inter({ subsets: ['latin'], variable: '--font-body', display: 'swap' })

export const dynamic = 'force-dynamic'

export async function generateMetadata(): Promise<Metadata> {
  const site = await getSite().catch(() => null)
  const name = site?.businessName || 'Label Trunc'
  const description =
    site?.description || 'New and pre-loved designer pieces, curated in Ampang. Shop online or visit the boutique.'
  return {
    metadataBase: new URL(getServerURL()),
    title: { default: `${name}: designer bags, new and pre-loved, in Ampang`, template: `%s · ${name}` },
    description,
    applicationName: name,
    icons: { icon: '/labeltrunc-icon.svg', apple: '/labeltrunc-icon.png' },
    openGraph: { type: 'website', siteName: name, locale: 'en_MY', description },
    twitter: { card: 'summary_large_image' },
    alternates: { canonical: '/' },
  }
}

export const viewport: Viewport = { themeColor: '#501C2B', width: 'device-width', initialScale: 1 }

export default async function RootLayout({ children }: { children: React.ReactNode }) {
  const { isEnabled: preview } = await draftMode()
  return (
    <html lang="en-MY" className={`${display.variable} ${body.variable}`}>
      <body>
        <a className="skip-link" href="#main">
          Skip to content
        </a>
        {preview ? (
          <div className="preview-bar" role="status">
            You are previewing unpublished changes. <Link href="/next/exit-preview">Exit preview</Link>
          </div>
        ) : null}
        <Header />
        <main id="main" tabIndex={-1}>
          {children}
        </main>
        <Footer />
        <RevealObserver />
      </body>
    </html>
  )
}
