import { expect, test } from '@playwright/test'
import { acceptEssentialOnly, waitForHydration } from './helpers'

test.beforeEach(async ({ context, baseURL }) => acceptEssentialOnly(context, baseURL!))

test('search dialog finds content and navigates with the keyboard', async ({ page, isMobile }) => {
  await page.goto('/')
  await waitForHydration(page)
  if (isMobile) await page.getByRole('button', { name: 'Search the website' }).first().click()
  else await page.keyboard.press('/')
  const input = page.getByRole('combobox', { name: 'Search the website' })
  await expect(input).toBeFocused()
  await input.fill('scholarship')
  const results = page.getByRole('listbox', { name: 'Search results' })
  await expect(results.getByRole('option').first()).toBeVisible()
  await input.press('ArrowDown')
  await input.press('Enter')
  await expect(page).toHaveURL(/\/admissions\/fees|\/learning\/sixth-form/)
})

test('search shows a helpful empty state', async ({ page }) => {
  await page.goto('/search?q=zzzxqv')
  await waitForHydration(page)
  await expect(page.getByRole('status')).toContainText('No results')
  await expect(page.getByRole('heading', { name: 'Popular pages' })).toBeVisible()
})

test('search API rejects nothing but returns structured results', async ({ request }) => {
  const res = await request.get('/api/site-search?q=bus')
  expect(res.ok()).toBe(true)
  const body = await res.json()
  expect(body.results.some((r: { title: string }) => /bus/i.test(r.title))).toBe(true)
})
