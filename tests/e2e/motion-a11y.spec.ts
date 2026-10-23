import AxeBuilder from '@axe-core/playwright'
import { expect, test } from '@playwright/test'
import { acceptEssentialOnly, waitForHydration } from './helpers'

test.beforeEach(async ({ context, baseURL }) => acceptEssentialOnly(context, baseURL!))

test.describe('reduced motion', () => {
  test('learning journey is not pinned and the values statement is static', async ({ page }) => {
    await page.emulateMedia({ reducedMotion: 'reduce' })
    await page.goto('/')
    await waitForHydration(page)
    await expect(page.locator('#learning-journey')).not.toHaveAttribute('data-pinned')
    await expect(page.locator('section[aria-labelledby^="kinetic"]')).not.toHaveAttribute('data-animated')
    await expect(page.getByRole('button', { name: /Pause moving words/ })).toHaveCount(0)
  })
})

test('the learning journey pins and scrolls sideways on large screens', async ({ page, isMobile }) => {
  test.skip(isMobile, 'pinning is desktop only')
  await page.goto('/')
  await waitForHydration(page)
  const section = page.locator('#learning-journey')
  await expect(section).toHaveAttribute('data-pinned')
  const track = section.locator('ol')
  const top = await section.evaluate((el) => el.getBoundingClientRect().top + window.scrollY)
  await page.evaluate((y) => window.scrollTo(0, y + 900), top)
  await expect.poll(async () => track.evaluate((el) => new DOMMatrix(getComputedStyle(el).transform).m41)).toBeLessThan(-100)
  await expect(section.getByRole('link', { name: 'Skip the learning journey' })).toBeVisible()
})

for (const path of ['/', '/learning/primary', '/admissions/visit', '/admissions/faqs', '/discover/results-accreditation', '/contact', '/discover/vision-values', '/admissions', '/community']) {
  test(`no serious accessibility violations on ${path}`, async ({ page }) => {
    await page.goto(path)
    await waitForHydration(page)
    await page.evaluate(() => document.querySelectorAll('[data-reveal]').forEach((el) => el.setAttribute('data-revealed', '')))
    await page.waitForTimeout(1200) // let reveal transitions finish so contrast is measured on the final state
    // @axe-core/playwright bundles its own Playwright types; the runtime object is compatible.
    const results = await new AxeBuilder({ page: page as never }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa', 'wcag22aa']).analyze()
    const serious = results.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical')
    expect(serious.map((v) => `${v.id}: ${v.nodes.map((n) => n.target.join(' ')).slice(0, 3).join(' | ')}`)).toEqual([])
  })
}
