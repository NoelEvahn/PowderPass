import type { BrowserContext, Page } from '@playwright/test'

/** Pretend the visitor already chose "reject optional" so the banner does not cover the page. */
export async function acceptEssentialOnly(context: BrowserContext, baseURL: string) {
  await context.addCookies([
    {
      name: 'isp_consent',
      value: encodeURIComponent(JSON.stringify({ version: 1, analytics: false, media: false, decidedAt: new Date().toISOString() })),
      url: baseURL,
    },
  ])
}

/** A unique client IP per test so the enquiry rate limiter does not interfere between runs. */
export const uniqueIp = () => `10.${Math.floor(Math.random() * 250)}.${Math.floor(Math.random() * 250)}.${Math.floor(Math.random() * 250)}`

/** Resolves once client components have hydrated (RevealObserver sets this flag in an effect). */
export const waitForHydration = (page: Page) => page.waitForFunction(() => (window as Window & { __ispReveal?: boolean }).__ispReveal === true)
