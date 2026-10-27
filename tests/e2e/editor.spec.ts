import { expect, test } from '@playwright/test'

import { ADMIN } from './global-setup'
import { state } from './helpers'

test.describe('editor', () => {
  test.skip(({ isMobile }) => isMobile, 'editor checks run on desktop')

  test('staff sign in and reach the dashboard shortcuts', async ({ page }) => {
    await page.goto('/admin')
    await page.getByLabel(/email/i).fill(ADMIN.email)
    await page.getByLabel(/password/i).fill(ADMIN.password)
    await page.getByRole('button', { name: /log ?in/i }).click()
    await expect(page.getByRole('heading', { name: 'Welcome to the Label Trunc editor' })).toBeVisible({ timeout: 30_000 })
    await expect(page.getByRole('link', { name: 'Add a piece' })).toBeVisible()
  })

  test('the public API never exposes orders, holds, enquiries or customers', async ({ request }) => {
    for (const collection of ['orders', 'reservations', 'enquiries', 'customers', 'users']) {
      const res = await request.get(`/api/${collection}`)
      expect(res.status(), collection).toBe(403)
    }
    const customers = await request.post('/api/customers', {
      data: { name: 'x', email: 'spam@example.com', password: 'long-enough-1' },
    })
    expect(customers.status()).toBe(403)
  })

  test('staff can manage stock through the API; drafts stay private', async ({ request }) => {
    const { token } = state()
    const auth = { Authorization: `JWT ${token}` }
    const brand = (await (await request.get('/api/brands?limit=1', { headers: auth })).json()).docs[0]
    const created = await request.post('/api/products?draft=true', {
      headers: auth,
      data: { title: 'E2E Draft Piece', brand: brand.id, price: 100, condition: 'good', gallery: [], _status: 'draft' },
    })
    expect(created.ok()).toBe(true)
    const { doc } = await created.json()
    const publicView = await request.get(`/shop/${doc.slug}`)
    expect(publicView.status()).toBe(404)
    const del = await request.delete(`/api/products/${doc.id}`, { headers: auth })
    expect(del.ok()).toBe(true)
  })

  test('the scheduled hold release needs the secret', async ({ request }) => {
    expect((await request.get('/tasks/release-holds')).status()).toBe(401)
    const ok = await request.get('/tasks/release-holds', { headers: { Authorization: 'Bearer e2e-cron-secret' } })
    expect(ok.status()).toBe(200)
  })

  test('the Billplz callback rejects unsigned requests', async ({ request }) => {
    const res = await request.post('/payments/billplz/callback', { form: { id: 'x', paid: 'true', x_signature: 'bad' } })
    expect([401, 503]).toContain(res.status())
  })
})
