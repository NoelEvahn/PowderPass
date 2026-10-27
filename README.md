# Label Trunc website (WordPress + WooCommerce)

The Label Trunc boutique site as a **WordPress theme** and a **companion plugin**, built for the existing cPanel WordPress hosting.

| Folder | What it is |
| --- | --- |
| `wordpress/themes/labeltrunc` | The theme: design, page templates, shop, piece page, cart, checkout and account styling, Customizer fields for contact details and home page text |
| `wordpress/plugins/labeltrunc-core` | The rules that must not depend on the theme: one-of-a-kind holds, no double sales, pay-at-boutique reservations, Billplz reconciliation, refunds that never relist automatically, piece details for staff, enquiry form, sign-in throttling, security headers, setup tool |
| `tests/php` | Commerce and security tests run inside a real WordPress + WooCommerce (separate test database) |
| `tests/e2e` | Browser tests (Playwright + axe) against a local WordPress |
| `tools/` | `build-zips.sh` (makes the upload files) and local development helpers |
| `docs/` | Editor guide, sources register, disclosure, design notes |

**Requirements:** WordPress 6.5+, WooCommerce 9+ (tested on WordPress 7.1.3 and WooCommerce 11.2.1), PHP 7.4+ declared, **tested on PHP 8.3**, MySQL or MariaDB (tested on MariaDB 10.11). The official **Billplz for WooCommerce** plugin for online payment.

---

## Deploying to the cPanel site

> Your live site is untouched by anything in this repository until someone uploads it. Do these steps **on a staging copy first**, then repeat on the live site once you are happy. Nothing here needs a destructive database change: the plugin only adds one table (`wp_lt_holds`) and some options.

### 1. Back up and make a staging copy

1. In cPanel, make a full backup (Files → Backup, or your host's backup tool) and download it.
2. Make a staging copy: if cPanel offers **WP Toolkit** or **Softaculous**, use its *Clone* / *Staging* feature onto a subdomain such as `staging.yourdomain`. Otherwise create a subdomain, copy the files, and import a copy of the database.
3. Discourage search engines on staging (Settings → Reading).

### 2. Build the upload files

```bash
./tools/build-zips.sh      # makes dist/labeltrunc-core.zip and dist/labeltrunc-theme.zip
```

### 3. Install, in this order (wp-admin on staging)

1. **Plugins → Add New**: install and activate **WooCommerce**. Skip the setup wizard's extras.
2. **Plugins → Add New**: install and activate **Billplz for WooCommerce** (by Billplz).
3. **Plugins → Add New → Upload**: `labeltrunc-core.zip`, activate.
4. **Appearance → Themes → Add New → Upload**: `labeltrunc-theme.zip`, activate.
5. **Tools → Label Trunc setup → Run setup.** It only fills what is empty and skips anything that already exists:
   - contact details and social links from the previous site (Customizer)
   - shop options: Malaysian ringgit, stock tracking, guest checkout, no tax, coupons off
   - "Reserve and pay at the boutique" turned on
   - a Malaysia shipping zone with *Collect at the boutique* (only if you have no zones yet)
   - pages (About, Buying info, Visit, Contact, Privacy, Privasi, Cookies, Terms, Refunds, Accessibility, Business details), **skipping any page whose address already exists**: check your existing pages afterwards
   - cart and checkout set to the classic layout this theme styles
   - categories *Top handle* and *Shoulder*, and the main and footer menus
6. **WooCommerce → Settings → Site visibility**: new WooCommerce installs start in *Coming soon* mode, which hides the shop from visitors. Switch to **Live** when you launch.

### 4. Real cron (holds must expire on time)

WordPress only runs scheduled jobs when someone visits. Holds, reservations and payment checks run every 5 minutes, so give it a real clock:

1. In `wp-config.php` add: `define( 'DISABLE_WP_CRON', true );`
2. In cPanel → **Cron Jobs**, every 5 minutes:
   `wget -q -O - "https://yourdomain/wp-cron.php?doing_wp_cron" >/dev/null 2>&1`

Expired holds are also cleared whenever someone tries to buy the piece, so a late cron never lets two people buy it. It only delays when the piece *shows* as available again.

### 5. Billplz (sandbox first)

1. Create a **Billplz sandbox** account (billplz-sandbox.com). Note the API secret key, X-Signature key and collection ID.
2. WooCommerce → Settings → Payments → **Billplz**: enter the sandbox keys and turn sandbox mode on. Enable the X-Signature check.
3. Do a full test purchase on staging with sandbox FPX and Touch 'n Go. Check the order goes to *Processing*, the piece shows *Sold*, and a second shopper cannot buy it.
4. Only then switch to the live keys on the live site. Which channels appear (Maybank2u, HSBC, Public Bank and other FPX banks, cards, Touch 'n Go eWallet) depends on what is enabled on your Billplz account.

Until Billplz is configured, checkout simply offers *Reserve and pay at the boutique*.

### 6. Email

Order, reservation, password-reset and "no longer available" emails go through WordPress mail. On shared hosting, PHP mail often lands in spam. Install an SMTP plugin (for example *WP Mail SMTP*) and connect it to your mailbox provider. Set where enquiries go in **Appearance → Customize → Label Trunc → Contact**.

### 7. Caching (LiteSpeed or any cache plugin)

- Never cache cart, checkout or my-account (LiteSpeed Cache and WooCommerce exclude them by default; check).
- Shop and piece pages show live status (*On hold*, *Reserved*, *Sold*). If they are cached for long, a piece can *look* available for a while after it is held. Purchases are still blocked correctly, because every add-to-cart and payment is checked against the database. Keep the public cache TTL short (5 minutes) or exclude `/shop/` and `/product/`.
- Do not minify or combine the theme's CSS/JS; they are already small.

### 8. Before launch

Work through `docs/DISCLOSURE.md` (business details, policy wording, first real pieces, sandbox payment, email). Then switch Site visibility to Live.

---

## Local development

Everything runs locally with PHP and MariaDB; nothing touches a real site.

```bash
# one-time: MariaDB with databases labeltrunc_wp and labeltrunc_wp_test (user ltwp)
php tools/local/install.php /path/to/wp-local          # installs WP, activates WooCommerce, Billplz, plugin, theme
php -S 127.0.0.1:8080 -t /path/to/wp-local tools/local/router.php
php tools/local/wp-eval.php /path/to/wp-local tools/local/seed-test-pieces.php   # labelled TEST pieces, localhost only

npm run test:php     # commerce + security tests in the separate *_test database
npm run test:e2e     # resets test pieces, then Playwright + axe against http://localhost:8080
```

The test runners refuse to run against anything that is not a local or `*_test` database.
