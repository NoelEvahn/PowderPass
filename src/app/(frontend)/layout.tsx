import type { Metadata, Viewport } from 'next'
import { Inter, Quicksand } from 'next/font/google'
import Script from 'next/script'
import type { ReactNode } from 'react'
import { Analytics } from '@/components/consent/Analytics'
import { ConsentProvider } from '@/components/consent/ConsentProvider'
import { CookieBanner } from '@/components/consent/CookieBanner'
import { SiteFooter } from '@/components/layout/SiteFooter'
import { SiteHeader } from '@/components/layout/SiteHeader'
import { MobileCta } from '@/components/layout/MobileCta'
import { HeadingDrift } from '@/components/motion/HeadingDrift'
import { ParallaxObserver } from '@/components/motion/ParallaxObserver'
import { ScrollProgress } from '@/components/motion/ScrollProgress'
import { RevealObserver } from '@/components/motion/RevealObserver'
import { resolveLink } from '@/lib/links'
import { getHeader, getSettings } from '@/lib/queries'
import { siteUrl } from '@/lib/seo'
import '@/styles/tokens.css'
import '@/styles/base.css'

const quicksand = Quicksand({ subsets: ['latin'], weight: ['600', '700'], variable: '--font-quicksand', display: 'swap' })
const inter = Inter({ subsets: ['latin'], variable: '--font-inter', display: 'swap' })

export async function generateMetadata(): Promise<Metadata> {
  const settings = await getSettings()
  return {
    metadataBase: new URL(siteUrl()),
    title: { default: settings.schoolName, template: `%s | ${settings.schoolName}` },
    description: settings.defaultDescription,
    applicationName: settings.schoolName,
    icons: { icon: '/brand/isp-crest.svg' },
    openGraph: { siteName: settings.schoolName, locale: 'en_GB', type: 'website' },
    twitter: { card: 'summary_large_image' },
  }
}

export const viewport: Viewport = {
  themeColor: '#211646',
  width: 'device-width',
  initialScale: 1,
  viewportFit: 'cover',
}

/*
 * Runs before first paint: marks JS as available so reveal animations can
 * start hidden. If the app never hydrates, a fallback reveals everything.
 */
const bootScript = `document.documentElement.classList.add('js');setTimeout(function(){if(!window.__ispReveal){document.querySelectorAll('[data-reveal]').forEach(function(e){e.setAttribute('data-revealed','')})}},3500)`

export default async function FrontendLayout({ children }: { children: ReactNode }) {
  const header = await getHeader()
  const cta = resolveLink(header.cta)

  return (
    <html lang="en-GB" className={`${quicksand.variable} ${inter.variable}`} suppressHydrationWarning>
      <body>
        <Script id="isp-boot" strategy="beforeInteractive">
          {bootScript}
        </Script>
        <ConsentProvider>
          <a className="skip-link" href="#main">
            Skip to main content
          </a>
          <SiteHeader />
          <main id="main" tabIndex={-1}>
            {children}
          </main>
          <SiteFooter />
          {cta && <MobileCta href={cta.href} label={cta.label} />}
          <CookieBanner />
          <Analytics measurementId={process.env.NEXT_PUBLIC_GA_MEASUREMENT_ID} />
          <RevealObserver />
          <ParallaxObserver />
          <HeadingDrift />
          <ScrollProgress />
        </ConsentProvider>
      </body>
    </html>
  )
}
