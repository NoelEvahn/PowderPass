import { expect, test } from '@playwright/test'

import { expectAccessible, expectNoHorizontalScroll } from './helpers'

const CONTENT_PAGES = ['/', '/about', '/info', '/visit', '/contact', '/privacy', '/privasi', '/cookies', '/terms', '/refunds', '/accessibility', '/business-details']

test.describe('understanding the boutique', () => {
  test('home explains what, where and how to buy', async ({ page }) => {
    await page.goto('/')
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
    await expect(page.getByText('11-2A, Level 2, Ukay Boulevard').first()).toBeVisible()
    await expect(page.getByRole('link', { name: /Shop the boutique/ })).toHaveAttribute('href', '/shop')
    await expect(page.getByRole('heading', { name: /Hold it, pay, take it home/ })).toBeVisible()
    // Real stock shows; no design-reference images from the old site.
    await expect(page.getByRole('link', { name: 'E2E Test Flap Bag' })).toBeVisible()
  })

  test('policy pages show what still awaits confirmation', async ({ page }) => {
    await page.goto('/terms')
    await expect(page.getByText(/awaiting confirmation/i).first()).toBeVisible()
    await expect(page.getByText(/Last updated/)).toBeVisible()
  })

  test('unknown pages give a helpful 404', async ({ page }) => {
    const res = await page.goto('/this-page-does-not-exist')
    expect(res?.status()).toBe(404)
    await expect(page.getByRole('link', { name: /shop/i }).first()).toBeVisible()
  })

  test('skip link moves to the main content', async ({ page, isMobile }) => {
    test.skip(isMobile, 'keyboard test')
    await page.goto('/about')
    await page.keyboard.press('Tab')
    const skip = page.getByRole('link', { name: 'Skip to content' })
    await expect(skip).toBeFocused()
    await skip.press('Enter')
    await expect(page.locator('main#main')).toBeFocused()
  })
})

test.describe('navigation', () => {
  test('every internal link resolves', async ({ page, request }) => {
    const seen = new Set<string>()
    for (const start of ['/', '/shop', '/info']) {
      await page.goto(start)
      const hrefs = await page.$$eval('a[href^="/"]', (as) => as.map((a) => (a as HTMLAnchorElement).getAttribute('href')!))
      hrefs.forEach((h) => seen.add(h.split('#')[0]))
    }
    for (const href of seen) {
      if (!href || href.startsWith('/admin')) continue
      const res = await request.get(href, { maxRedirects: 5 })
      expect(res.status(), href).toBeLessThan(400)
    }
    expect(seen.size).toBeGreaterThan(15)
  })

  test('mobile menu opens, navigates and closes with Escape', async ({ page, isMobile }) => {
    test.skip(!isMobile, 'mobile only')
    await page.goto('/')
    const button = page.getByRole('button', { name: 'Menu' })
    await button.click()
    const dialog = page.getByRole('dialog', { name: 'Menu' })
    await expect(dialog).toBeVisible()
    await page.keyboard.press('Escape')
    await expect(dialog).toBeHidden()
    await expect(button).toBeFocused()
    await button.click()
    await dialog.getByRole('link', { name: 'Visit' }).click()
    await expect(page).toHaveURL(/\/visit$/)
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
  })
})

test.describe('accessibility and layout', () => {
  for (const path of CONTENT_PAGES) {
    test(`axe: ${path}`, async ({ page }) => {
      await page.goto(path)
      await expectAccessible(page)
    })
  }

  test('no horizontal scroll at any listed width', async ({ page, isMobile }) => {
    test.skip(isMobile, 'width sweep runs once')
    for (const width of [320, 390, 412, 768, 820, 1024, 1366, 1440, 1920]) {
      await page.setViewportSize({ width, height: 900 })
      for (const path of ['/', '/shop', '/visit', '/contact', '/info']) {
        await page.goto(path)
        await expectNoHorizontalScroll(page)
      }
    }
  })

  test('reduced motion shows every word of the statement lit and unpinned', async ({ browser }) => {
    const context = await browser.newContext({ reducedMotion: 'reduce' })
    const page = await context.newPage()
    await page.goto('/')
    const lit = page.locator('section.lit')
    await expect(lit).not.toHaveAttribute('data-lit', 'scroll')
    await expect(page.locator('[data-reveal]').first()).toHaveCSS('opacity', '1')
    await context.close()
  })
})

test('contact form sends an enquiry', async ({ page }) => {
  await page.goto('/contact?topic=visit')
  await page.getByRole('textbox', { name: /^Name/ }).fill('E2E Visitor')
  await page.getByRole('textbox', { name: /^Email/ }).fill('visitor@example.com')
  await page.getByRole('textbox', { name: /^Message/ }).fill('Could I visit on Saturday afternoon?')
  await page.getByLabel(/I agree/).check()
  await page.waitForTimeout(2600) // minimum fill time for the spam check
  await page.getByRole('button', { name: /send/i }).click()
  await expect(page.getByRole('status').filter({ hasText: /Thank you/ })).toBeVisible()
})
