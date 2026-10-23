import { expect, test } from '@playwright/test'
import { acceptEssentialOnly, uniqueIp, waitForHydration } from './helpers'

test.beforeEach(async ({ context, baseURL }) => {
  await acceptEssentialOnly(context, baseURL!)
  await context.setExtraHTTPHeaders({ 'x-real-ip': uniqueIp() })
})

test('shows an error summary and keeps answers when the form is incomplete', async ({ page }) => {
  await page.goto('/admissions/visit')
  await waitForHydration(page)
  const form = page.locator('form').filter({ has: page.getByRole('button', { name: 'Request a visit' }) })
  await form.getByLabel('Your name (parent or carer)').fill('Test Parent')
  await form.getByRole('button', { name: 'Request a visit' }).click()
  const summary = page.getByRole('alert').filter({ hasText: 'Please check' })
  await expect(summary).toContainText('Please check')
  await expect(summary).toContainText('Email address')
  await expect(summary).toContainText('Privacy notice')
  await expect(form.getByLabel('Your name (parent or carer)')).toHaveValue('Test Parent')
  await expect(form.getByLabel('Email address')).toHaveAttribute('aria-invalid', 'true')
})

test('validates a field after the visitor leaves it', async ({ page }) => {
  await page.goto('/admissions/visit')
  await waitForHydration(page)
  const email = page.getByLabel('Email address')
  await email.fill('not-an-email')
  await email.blur()
  await expect(page.getByText('Please enter a valid email address')).toBeVisible()
  await email.fill('parent@example.com')
  await expect(page.getByText('Please enter a valid email address')).toBeHidden()
})

test('a valid enquiry is stored and confirmed with a reference', async ({ page }) => {
  await page.goto('/admissions/visit')
  await waitForHydration(page)
  await page.getByLabel('Your name (parent or carer)').fill('E2E Test Parent')
  await page.getByLabel('Email address').fill('e2e-parent@example.com')
  await page.getByLabel('Stage of interest').selectOption('primary')
  await page.getByLabel(/Anything you would like us to know/).fill('Automated test submission — please ignore.')
  await page.getByLabel(/I have read the privacy notice/).check()
  await page.waitForTimeout(2600) // the form rejects submissions faster than a person could type
  await page.getByRole('button', { name: 'Request a visit' }).click()
  const status = page.getByRole('status').filter({ hasText: 'Your enquiry has reached' })
  await expect(status).toBeVisible()
  await expect(status).toContainText(/ISP-\d{5}/)
})

test('enquiries cannot be created or read through the public API', async ({ request }) => {
  const create = await request.post('/api/enquiries', { data: { type: 'enquiry', parentName: 'x', email: 'x@example.com' } })
  expect(create.status()).toBe(403)
  const read = await request.get('/api/enquiries')
  expect(read.status()).toBe(403)
})
