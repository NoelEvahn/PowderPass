import { expect, test } from '@playwright/test'
import { acceptEssentialOnly, waitForHydration } from './helpers'

test.beforeEach(async ({ context, baseURL }) => acceptEssentialOnly(context, baseURL!))

test('home page renders the promise, journey and values', async ({ page }) => {
  await page.goto('/')
  await waitForHydration(page)
  await expect(page.getByRole('heading', { level: 1 })).toContainText('A place to belong.')
  await expect(page.getByRole('heading', { name: 'Find their next chapter.' })).toBeVisible()
  await expect(page.getByRole('heading', { name: /Curiosity\. Connection\. Contribution\./ })).toBeAttached()
  await expect(page).toHaveTitle(/The International School @ ParkCity/)
})

test('desktop menu opens on click, closes with Escape and restores focus', async ({ page, isMobile }) => {
  test.skip(isMobile, 'desktop navigation only')
  await page.goto('/')
  await waitForHydration(page)
  const button = page.getByRole('button', { name: 'Admissions' })
  await button.click()
  await expect(button).toHaveAttribute('aria-expanded', 'true')
  const panel = page.getByRole('region', { name: 'Admissions' })
  await expect(panel.getByRole('link', { name: /Fees and scholarships/ })).toBeVisible()
  await page.keyboard.press('Escape')
  await expect(button).toHaveAttribute('aria-expanded', 'false')
  await expect(button).toBeFocused()
  await button.click()
  await panel.getByRole('link', { name: /Fees and scholarships/ }).click()
  await expect(page).toHaveURL(/\/admissions\/fees$/)
  await expect(page.getByRole('heading', { level: 1 })).toContainText('Fees and')
})

test('mobile menu is a dialog that closes with Escape', async ({ page, isMobile }) => {
  test.skip(!isMobile, 'mobile navigation only')
  await page.goto('/')
  await waitForHydration(page)
  await page.getByRole('button', { name: 'Menu' }).click()
  const dialog = page.getByRole('dialog', { name: 'Menu' })
  await expect(dialog).toBeVisible()
  await dialog.getByRole('button', { name: 'Learning' }).click()
  await expect(dialog.getByRole('link', { name: 'Sixth Form' })).toBeVisible()
  await page.keyboard.press('Escape')
  await expect(dialog).toBeHidden()
  await page.getByRole('button', { name: 'Menu' }).click()
  await page.getByRole('dialog', { name: 'Menu' }).getByRole('button', { name: 'Learning' }).click()
  await page.getByRole('dialog', { name: 'Menu' }).getByRole('link', { name: 'Sixth Form' }).click()
  await expect(page).toHaveURL(/\/learning\/sixth-form$/)
})

test('breadcrumbs, skip link and stage navigation work', async ({ page }) => {
  await page.goto('/learning/primary')
  await waitForHydration(page)
  await expect(page.getByRole('navigation', { name: 'Breadcrumb' })).toContainText('Learning')
  await page.keyboard.press('Tab')
  await expect(page.getByRole('link', { name: 'Skip to main content' })).toBeFocused()
  await page.getByRole('link', { name: /Next: Secondary/ }).click()
  await expect(page).toHaveURL(/\/learning\/secondary$/)
})

test('unknown pages return a helpful 404 and old URLs redirect', async ({ page }) => {
  const res = await page.goto('/this-page-does-not-exist')
  await waitForHydration(page)
  expect(res?.status()).toBe(404)
  await expect(page.getByRole('heading', { level: 1 })).toContainText('could not find that page')
  await page.goto('/about-us/welcome/')
  await waitForHydration(page)
  await expect(page).toHaveURL(/\/discover\/leadership$/)
})

test('FAQ accordion expands, filters and supports deep links', async ({ page }) => {
  await page.goto('/admissions/faqs')
  await waitForHydration(page)
  const question = page.getByRole('button', { name: 'Is there a school bus service?' })
  await expect(question).toHaveAttribute('aria-expanded', 'false')
  await question.click()
  await expect(question).toHaveAttribute('aria-expanded', 'true')
  await expect(page.getByText(/TNR Transport/)).toBeVisible()
  await page.getByLabel('Filter questions').fill('uniform')
  await expect(page.getByRole('status').filter({ hasText: 'questions shown' })).toContainText('1 of')
})

test('image gallery opens an accessible viewer', async ({ page }) => {
  await page.goto('/discover/results-accreditation')
  await waitForHydration(page)
  await page.getByRole('button', { name: /View larger: A-Level/ }).click()
  const viewer = page.getByRole('dialog')
  await expect(viewer).toBeVisible()
  await viewer.getByRole('button', { name: 'Next image' }).click()
  await expect(viewer).toContainText('2 / 3')
  await page.keyboard.press('Escape')
  await expect(viewer).toBeHidden()
})

test('hero gallery turns with arrow keys and page scroll', async ({ page }) => {
  await page.goto('/')
  await waitForHydration(page)
  const gallery = page.getByRole('region', { name: 'Life at ISP in photographs' })
  await expect(gallery).toContainText('01 / 07')
  await expect(gallery.getByText('Scroll or drag to explore')).toBeVisible()
  const stage = gallery.locator('[tabindex="0"]')
  await stage.focus()
  await page.keyboard.press('ArrowRight')
  await expect(gallery).toContainText('02 / 07')
  await page.keyboard.press('ArrowLeft')
  await expect(gallery).toContainText('01 / 07')
  const reduced = await page.evaluate(() => matchMedia('(prefers-reduced-motion: reduce)').matches)
  if (!reduced) {
    await page.evaluate(() => window.scrollTo(0, 330))
    await expect(gallery).not.toContainText('01 / 07')
  }
})

test('values spiral shows every value with its tagline and no descriptions', async ({ page }) => {
  await page.goto('/discover/vision-values')
  const values = page.getByRole('list', { name: 'Eight Core Values' })
  await expect(values.getByRole('listitem')).toHaveCount(8)
  await expect(values).toContainText('Treat everybody the same way')
  await expect(values).not.toContainText('Respecting the rights and status of others')
})

test('long text opens with a Read more control that expands it', async ({ page }) => {
  await page.goto('/discover/leadership')
  await waitForHydration(page)
  const toggle = page.getByRole('button', { name: /Read more/ }).first()
  await expect(toggle).toHaveAttribute('aria-expanded', 'false')
  await toggle.click()
  await expect(page.getByRole('button', { name: 'Show less' }).first()).toHaveAttribute('aria-expanded', 'true')
})
