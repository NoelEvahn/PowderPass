import { expect, test } from '@playwright/test'
import { waitForHydration } from './helpers'

test('first visit shows Accept, Reject and Customise; rejecting stores the choice', async ({ page, context }) => {
  await page.goto('/')
  await waitForHydration(page)
  const banner = page.getByRole('region', { name: 'Cookies on this website' })
  await expect(banner).toBeVisible()
  await expect(banner.getByRole('button', { name: 'Accept all' })).toBeVisible()
  await banner.getByRole('button', { name: 'Reject optional' }).click()
  await expect(banner).toBeHidden()
  const cookie = (await context.cookies()).find((c) => c.name === 'isp_consent')
  expect(JSON.parse(decodeURIComponent(cookie!.value))).toMatchObject({ analytics: false, media: false })
  await page.reload()
  await expect(page.getByRole('region', { name: 'Cookies on this website' })).toBeHidden()
})

test('preferences can be customised and changed later from the footer', async ({ page, context }) => {
  await page.goto('/')
  await waitForHydration(page)
  await page.getByRole('region', { name: 'Cookies on this website' }).getByRole('button', { name: 'Customise' }).click()
  const dialog = page.getByRole('dialog', { name: 'Cookie settings' })
  await dialog.getByRole('switch', { name: /Maps and third-party media/ }).check()
  await dialog.getByRole('button', { name: 'Save choices' }).click()
  let value = JSON.parse(decodeURIComponent((await context.cookies()).find((c) => c.name === 'isp_consent')!.value))
  expect(value).toMatchObject({ analytics: false, media: true })

  await page.getByRole('button', { name: 'Cookie settings' }).click()
  await page.getByRole('dialog', { name: 'Cookie settings' }).getByRole('button', { name: 'Reject optional' }).click()
  value = JSON.parse(decodeURIComponent((await context.cookies()).find((c) => c.name === 'isp_consent')!.value))
  expect(value).toMatchObject({ media: false })
})

test('embedded maps load only after the visitor allows them', async ({ page }) => {
  await page.goto('/contact')
  await waitForHydration(page)
  await page.getByRole('region', { name: 'Cookies on this website' }).getByRole('button', { name: 'Reject optional' }).click()
  await expect(page.locator('iframe[title^="Map of"]')).toHaveCount(0)
  await page.getByRole('button', { name: 'Allow maps and load' }).first().click()
  await expect(page.locator('iframe[title^="Map of"]').first()).toBeAttached()
})

test('no analytics script loads without consent', async ({ page }) => {
  const requests: string[] = []
  page.on('request', (r) => requests.push(r.url()))
  await page.goto('/')
  await waitForHydration(page)
  await page.waitForLoadState('networkidle')
  expect(requests.some((u) => u.includes('googletagmanager') || u.includes('google-analytics'))).toBe(false)
})
