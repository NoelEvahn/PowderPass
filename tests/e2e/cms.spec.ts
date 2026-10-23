import { expect, test } from '@playwright/test'

test('draft preview requires an editor session', async ({ request }) => {
  const res = await request.get('/next/preview?path=/admissions', { maxRedirects: 0 })
  expect(res.status()).toBe(403)
})

test('preview refuses open redirects', async ({ request }) => {
  const res = await request.get('/next/preview?path=//evil.example', { maxRedirects: 0 })
  expect(res.status()).toBe(400)
})

test('editor sign-in page loads and public user sign-up is not possible', async ({ page, request }) => {
  await page.goto('/admin')
  await expect(page.getByLabel(/email/i)).toBeVisible()
  const res = await request.post('/api/users', { data: { email: 'intruder@example.com', password: 'x1234567!', name: 'x', roles: ['admin'] } })
  expect(res.status()).toBe(403)
})

test('robots, sitemap and metadata are in place', async ({ request, page }) => {
  expect(await (await request.get('/robots.txt')).text()).toContain('Disallow: /admin')
  const sitemap = await (await request.get('/sitemap.xml')).text()
  expect(sitemap).toContain('/learning/sixth-form')
  await page.goto('/learning/early-years')
  await expect(page.locator('link[rel="canonical"]')).toHaveAttribute('href', /\/learning\/early-years$/)
  await expect(page.locator('meta[property="og:title"]')).toHaveAttribute('content', /Early Years/)
  expect(await page.locator('script[type="application/ld+json"]').count()).toBeGreaterThan(0)
})

test('security headers are sent', async ({ request }) => {
  const res = await request.get('/')
  expect(res.headers()['content-security-policy']).toContain("frame-ancestors 'self'")
  expect(res.headers()['x-content-type-options']).toBe('nosniff')
  expect(res.headers()['x-powered-by']).toBeUndefined()
})
