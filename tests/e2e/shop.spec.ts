import { expect, test, type Page } from '@playwright/test'

import { expectAccessible, state } from './helpers'

const addToCart = async (page: Page, slug: string) => {
  await page.goto(`/shop/${slug}`)
  await page.getByRole('button', { name: 'Add to cart' }).first().click()
}

test.describe('shop', () => {
  test('lists real stock with filters, and only shows published pieces', async ({ page }) => {
    await page.goto('/shop')
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
    await expect(page.getByRole('link', { name: 'E2E Test Flap Bag' })).toBeVisible()
    await expect(page.getByRole('link', { name: 'E2E Test Sold Bag' })).toBeVisible()
    await page.goto('/shop?q=Tote')
    await expect(page.getByRole('link', { name: 'E2E Test Tote' })).toBeVisible()
    await expect(page.getByRole('link', { name: 'E2E Test Flap Bag' })).toHaveCount(0)
    await page.goto('/shop?sold=0')
    await expect(page.getByRole('link', { name: 'E2E Test Sold Bag' })).toHaveCount(0)
  })

  test('product page shows details; sold and boutique-only pieces have no cart button', async ({ page }) => {
    const s = state()
    await page.goto(`/shop/${s.available.slug}`)
    await expect(page.getByRole('heading', { level: 1, name: 'E2E Test Flap Bag' })).toBeVisible()
    await expect(page.getByText('RM 12,500').first()).toBeVisible()
    await expect(page.getByText('Dust bag').first()).toBeVisible()
    await page.goto(`/shop/${s.sold.slug}`)
    await expect(page.getByRole('button', { name: 'Add to cart' })).toHaveCount(0)
    await page.goto(`/shop/${s.boutique.slug}`)
    await expect(page.getByRole('button', { name: 'Add to cart' })).toHaveCount(0)
    await expect(page.getByRole('link', { name: /Ask about this piece/ }).first()).toBeVisible()
  })

  test('gallery thumbnails switch the main photo', async ({ page, isMobile }) => {
    test.skip(isMobile, 'desktop')
    await page.goto(`/shop/${state().available.slug}`)
    await expect(page.getByText('1 of 2').first()).toBeVisible()
    await page.getByRole('button', { name: /photo 2|image 2|2 of 2|Show photo 2/i }).first().click()
    await expect(page.getByText('2 of 2').first()).toBeVisible()
  })

  test('hold, reserve to pay at the boutique, then cancel', async ({ page, browser, isMobile }) => {
    test.skip(isMobile, 'one checkout run is enough; mobile checks layout below')
    const s = state()
    await addToCart(page, s.second.slug)
    await expect(page.getByText(/Added to your cart|Already in your cart/)).toBeVisible()

    // Another shopper sees it on hold and cannot add it.
    const other = await browser.newContext()
    const otherPage = await other.newPage()
    await otherPage.goto(`/shop/${s.second.slug}`)
    await expect(otherPage.getByText(/On hold/).first()).toBeVisible()
    await expect(otherPage.getByRole('button', { name: 'Add to cart' })).toHaveCount(0)

    await page.goto('/cart')
    await expect(page.getByRole('link', { name: 'E2E Test Tote' })).toBeVisible()
    await expect(page.getByText(/Held/).first()).toBeVisible()
    await page.getByRole('link', { name: /checkout/i }).first().click()

    await expect(page).toHaveURL(/\/checkout/)
    await page.getByLabel('Full name').fill('E2E Shopper')
    await page.getByLabel('Email').fill('shopper@example.com')
    await page.getByLabel('Phone / WhatsApp').fill('+60 12-345 6789')
    await page.getByLabel(/Reserve now, pay at the boutique/).check()
    await page.locator('#agree').check()
    await page.getByRole('button', { name: 'Reserve my piece' }).click()

    await expect(page).toHaveURL(/\/order\/LT\d{6}-[A-Z0-9]{5}\?t=/)
    await expect(page.getByText(/Reserved/).first()).toBeVisible()
    const orderURL = page.url()

    // Without the secret token the order is not shown.
    const noToken = await otherPage.goto(orderURL.split('?')[0])
    expect(noToken?.status()).toBe(404)

    await page.getByRole('button', { name: 'Cancel this order' }).click()
    await expect(page.getByText(/cancelled/i).first()).toBeVisible()

    await otherPage.goto(`/shop/${s.second.slug}`)
    await expect(otherPage.getByRole('button', { name: 'Add to cart' }).first()).toBeVisible()
    await other.close()
  })

  test('online payment is not offered until Billplz is configured', async ({ page, isMobile }) => {
    test.skip(isMobile, 'desktop')
    await addToCart(page, state().available.slug)
    await page.goto('/checkout')
    const online = page.getByLabel(/Pay online now/)
    if (await online.count()) await expect(online).toBeDisabled()
    await page.goto('/cart')
    await page.getByRole('button', { name: /Remove/ }).first().click()
    await expect(page.getByText('Your cart is empty')).toBeVisible()
  })

  test('accounts: register, sign out, sign in', async ({ page, isMobile }) => {
    test.skip(isMobile, 'desktop')
    const email = `e2e-${Date.now()}@example.com`
    await page.goto('/account/register')
    await page.getByLabel('Full name').fill('E2E Customer')
    await page.getByLabel('Email').fill(email)
    await page.getByLabel('Password').fill('a-long-password-1')
    await page.getByRole('button', { name: 'Create account' }).click()
    await expect(page).toHaveURL(/\/account$/)
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
    await page.getByRole('button', { name: /Sign out/ }).click()
    await expect(page).toHaveURL(/\/$/)
    await page.goto('/account/sign-in')
    await page.getByLabel('Email').fill(email)
    await page.getByLabel('Password').fill('wrong-password-1')
    await page.getByRole('button', { name: 'Sign in' }).click()
    await expect(page.getByText(/do not match/)).toBeVisible()
    await page.getByLabel('Password').fill('a-long-password-1')
    await page.getByRole('button', { name: 'Sign in' }).click()
    await expect(page).toHaveURL(/\/account$/)
  })

  for (const path of ['/shop', '/cart', '/checkout', '/account/sign-in', '/account/register']) {
    test(`axe: ${path}`, async ({ page }) => {
      await page.goto(path)
      await expectAccessible(page)
    })
  }

  test('axe: product page', async ({ page }) => {
    await page.goto(`/shop/${state().available.slug}`)
    await expectAccessible(page)
  })
})
