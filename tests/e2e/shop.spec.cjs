// End-to-end checks of the shopper journey, holds across two shoppers, enquiries,
// accessibility (axe), layout overflow and internal links. Uses labelled test pieces only.
const { test, expect } = require('@playwright/test');
const AxeBuilder = require('@axe-core/playwright').default;

const PAGES = ['/', '/shop/', '/product/test-piece-01/', '/visit/', '/contact/', '/info/', '/about/', '/cart/', '/my-account/', '/privacy/', '/terms/', '/refunds/'];

test.describe('pages', () => {
  for (const path of PAGES) {
    test(`axe and overflow ${path}`, async ({ page }) => {
      const res = await page.goto(path);
      expect(res.status()).toBeLessThan(400);
      const axe = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa', 'wcag22aa']).analyze();
      const bad = axe.violations.filter((v) => ['serious', 'critical'].includes(v.impact));
      expect(bad.map((v) => `${v.id}: ${v.nodes.map((n) => n.target.join(' ')).join(', ')}`)).toEqual([]);
      for (const width of [320, 390, 768]) {
        await page.setViewportSize({ width, height: 800 });
        const over = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
        expect(over, `horizontal overflow at ${width}px`).toBeLessThanOrEqual(0);
      }
    });
  }

  test('internal links and images resolve', async ({ page, request }) => {
    const seen = new Set();
    for (const path of ['/', '/shop/', '/product/test-piece-01/', '/info/']) {
      await page.goto(path);
      const urls = await page.evaluate(() => [
        ...[...document.querySelectorAll('a[href]')].map((a) => a.href),
        ...[...document.images].map((i) => i.currentSrc || i.src),
      ]);
      for (const u of urls) {
        const url = new URL(u);
        if (url.origin !== new URL(page.url()).origin || /wp-admin|add-to-cart|customer-logout/.test(url.href)) continue;
        url.hash = '';
        if (seen.has(url.href)) continue;
        seen.add(url.href);
        const r = await request.get(url.href, { maxRedirects: 5 });
        expect(r.status(), url.href).toBeLessThan(400);
      }
    }
    expect(seen.size).toBeGreaterThan(20);
  });

  test('404 page', async ({ page }) => {
    const res = await page.goto('/no-such-page-here/');
    expect(res.status()).toBe(404);
    await expect(page.locator('h1')).toBeVisible();
  });
});

test.describe('holds and checkout', () => {
  test('a piece in one cart is held for everyone else, then reserved at checkout', async ({ browser }) => {
    const a = await (await browser.newContext()).newPage();
    const b = await (await browser.newContext()).newPage();

    await a.goto('/product/test-piece-02/');
    await a.getByRole('button', { name: 'Add to cart' }).click();
    await a.goto('/cart/');
    await expect(a.locator('.woocommerce-cart-form td.product-name').getByRole('link', { name: 'Test piece 02' })).toBeVisible();
    await expect(a.getByText(/Held for you until/)).toBeVisible();

    await b.goto('/product/test-piece-02/');
    await expect(b.getByRole('button', { name: 'Add to cart' })).toHaveCount(0);
    await expect(b.locator('#lt-buy')).toContainText(/Someone is holding this piece/);

    // Forcing the add-to-cart URL must not steal the hold either.
    const id = await b.locator('[id^="product-"]').first().getAttribute('id');
    await b.goto(`/product/test-piece-02/?add-to-cart=${id.replace('product-', '')}`);
    await b.goto('/cart/');
    await expect(b.locator('.woocommerce-cart-form')).toHaveCount(0);

    // Shopper A reserves it to pay at the boutique.
    await a.goto('/checkout/');
    await a.fill('#billing_first_name', 'Test');
    await a.fill('#billing_last_name', 'Shopper');
    await a.fill('#billing_phone', '0123456789');
    await a.fill('#billing_email', 'test-shopper@example.com');
    const pickup = a.locator('input[name^="shipping_method"][value^="local_pickup"]');
    if (await pickup.count() && (await pickup.first().getAttribute('type')) === 'radio') await pickup.first().check();
    await a.waitForLoadState('networkidle');
    const pay = a.locator('#payment_method_lt_pay_at_shop');
    await expect(pay).toHaveCount(1);
    if ((await pay.getAttribute('type')) === 'radio') await pay.check();
    const terms = a.locator('#terms');
    if (await terms.count()) await terms.check();
    await a.locator('#place_order').click();
    await a.waitForURL(/order-received/, { timeout: 30000 });
    await expect(a.locator('body')).toContainText(/reserved/i);

    await b.goto('/product/test-piece-02/');
    await expect(b.locator('#lt-buy')).toContainText(/Reserved/i);
    await b.goto('/shop/');
    await expect(b.locator('.lt-card', { hasText: 'Test piece 02' })).toContainText(/Reserved/i);
  });

  test('removing a piece from the cart releases it', async ({ browser }) => {
    const a = await (await browser.newContext()).newPage();
    const b = await (await browser.newContext()).newPage();
    await a.goto('/product/test-piece-03/');
    await a.getByRole('button', { name: 'Add to cart' }).click();
    await a.goto('/cart/');
    await a.locator('a.remove').first().click();
    await expect(a.locator('.cart-empty').first()).toBeVisible();
    await b.goto('/product/test-piece-03/');
    await expect(b.getByRole('button', { name: 'Add to cart' })).toBeVisible();
  });

  test('sold pieces cannot be bought', async ({ page }) => {
    await page.goto('/product/test-piece-07/');
    await expect(page.getByRole('button', { name: 'Add to cart' })).toHaveCount(0);
    await expect(page.locator('#lt-buy')).toContainText('Sold');
  });
});

test.describe('enquiries and menu', () => {
  test('enquiry form validates, then sends', async ({ page }) => {
    await page.goto('/contact/?topic=piece');
    await page.waitForTimeout(3200); // the form rejects submissions faster than a person could type
    await page.fill('#lt-message', 'Test enquiry from the browser tests.');
    await page.locator('#lt-consent').check();
    await page.locator('form.lt-form button[type="submit"]').click();
    await expect(page.locator('.lt-form-status--error')).toBeVisible();
    await expect(page.locator('#lt-name')).toHaveAttribute('aria-invalid', 'true');
    await page.waitForTimeout(3200);
    await page.fill('#lt-name', 'Test Person');
    await page.fill('#lt-email', 'test-person@example.com');
    if (!(await page.locator('#lt-consent').isChecked())) await page.locator('#lt-consent').check();
    await page.locator('form.lt-form button[type="submit"]').click();
    await expect(page).toHaveURL(/enquiry=sent/);
    await expect(page.locator('.lt-form-status--success')).toBeVisible();
  });

  test('phone menu opens and closes with Escape', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto('/');
    await page.locator('[data-lt-menu-open]').click();
    await expect(page.locator('dialog[data-lt-menu]')).toBeVisible();
    await page.keyboard.press('Escape');
    await expect(page.locator('dialog[data-lt-menu]')).toBeHidden();
  });

  test('shop filters work without a full reload of state', async ({ page }) => {
    await page.goto('/shop/?condition=unused');
    await expect(page.locator('select[name="condition"]')).toHaveValue('unused');
    await page.goto('/shop/?s=zzzz-nothing&post_type=product');
    await expect(page.locator('.lt-empty')).toContainText('No pieces match');
  });
});

test.describe('staff', () => {
  test('staff sign in and see the dashboard and piece fields', async ({ page }) => {
    await page.goto('/wp-login.php');
    await page.fill('#user_login', 'admin');
    await page.fill('#user_pass', process.env.LT_ADMIN_PASSWORD || 'local-admin-password-1');
    await page.click('#wp-submit');
    await page.waitForURL(/wp-admin/);
    await expect(page.getByRole('heading', { name: 'Label Trunc today' })).toBeVisible();
    await page.goto('/wp-admin/post-new.php?post_type=product');
    await expect(page.getByText('Piece details').first()).toBeVisible();
  });
});
