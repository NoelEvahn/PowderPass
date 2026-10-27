# Disclosure: what is unfinished, unconfirmed or untested

Read this before launch. It is kept honest on purpose.

## Needs Label Trunc's input before launch

| Item | Where | What to do |
| --- | --- | --- |
| Real pieces and photos | Products | The shop is empty until you add pieces, and says so. No stock photos or generated images are included. |
| Opening hours | Customize → Label Trunc → Contact | Enter them, or leave empty to keep "Appointments recommended…". |
| Registered name and SSM number | Customize → Business; *Business details*, *Privacy*, *Terms* pages | Enter them and replace the bracketed placeholders. |
| Policy wording | Privacy, Privasi, Cookies, Terms, Refunds, Accessibility, Business details | Each shows "parts await confirmation". Placeholders still in the text: [registered business or company name], [SSM registration number], [48 hours], [14 days], [2 working days], [to be added by Label Trunc] / [akan ditambah oleh Label Trunc]. Have refund and terms text checked by someone qualified, then untick the confirmation box on each page. |
| Delivery | WooCommerce → Settings → Shipping | Only *Collect at the boutique* is set up. "Same-day delivery, Klang Valley" (from the old site) appears on the Buying info page; add a delivery method with the real fee and areas before promising it at checkout. A delivery order always requires an address. |
| Billplz account | WooCommerce → Settings → Payments | Without keys, checkout only offers *Reserve and pay at the boutique*. The home page's "how buying works" steps adapt automatically to whether Billplz is enabled; the *Buying info*, *Terms* and *Privacy* pages describe Billplz payment, so edit them if you launch without it. |
| SMTP email | Plugin of your choice | See README step 6. |
| Site visibility | WooCommerce → Settings → Site visibility | Switch from *Coming soon* to *Live* at launch. |
| Existing pages and menus on the live site | Pages, Appearance → Menus | Setup skips pages whose address already exists and assigns its own menus. Review both after running it. |

## Built but not verified here

- **Billplz payments.** The plugin uses the official *Billplz for WooCommerce* plugin for bills and callbacks, and adds double-sale protection around it (a database lock, duplicate-callback handling, late payments → *Refund due*, a check with Billplz before an unpaid order is cancelled). Those rules are tested by calling WooCommerce's own payment-complete path directly, including parallel duplicate callbacks. **No request was sent to Billplz, sandbox or live**, because no credentials were used. Do one full sandbox purchase (FPX and Touch 'n Go) on staging before going live.
- **Your hosting.** Everything ran on a local copy (PHP 8.3, MariaDB 10.11, WordPress 7.1.3, WooCommerce 11.2.1). Not run on your cPanel server, with your other plugins, your PHP version, LiteSpeed, or your existing content. Use staging first (README).
- **Email delivery.** Emails are generated and captured in tests; none was sent through a real mail server.
- **Real devices and screen readers.** Tested in Chromium at 320–1440 px. Not checked on a physical iPhone (Safari) or Android phone, or with VoiceOver/TalkBack.
- **The visitor hold cap** counts holds per connection (hashed IP address). Several people on one shop or office Wi-Fi share that limit (default 5 pieces); people on mobile networks may share IPs too. Raise it if customers report "too many pieces held".

## Test results at hand-over

Run on the local copy described above, on 27 October 2026.

- **PHP commerce and security tests** (`npm run test:php`, real WordPress + WooCommerce, separate test database): **19 passing, 0 failing.** They include 20 shoppers adding the same piece at the same moment in parallel processes (exactly one wins), a checkout submitted twice at once (holds claimed once), duplicate payment confirmations sequentially and 6 in parallel (one payment recorded), a boutique sale during an online checkout (order cancelled, late payment flagged), late payment after expiry (kept if the piece is still free, *Refund due* with stock unchanged if someone else holds it), cancelling or refunding a paid order never relisting, an unpaid order cancelled when its hold ends, pay-at-boutique reservation, its expiry, and staff marking it paid, hold caps and the 24-hour ceiling, sign-in lockout, private REST user list, and automatic stock numbers. Upload limits and enquiry anti-spam are implemented but have no automated test.
- **Browser tests** (`npm run test:e2e`, Playwright + axe in Chromium): **21 passing, 0 failing.** axe WCAG 2.0/2.1/2.2 A and AA with zero serious or critical issues (moderate and minor issues are not failed on) on 12 pages; no horizontal scroll at 320, 390 and 768 px; every internal link and image on four key pages returns OK; a held piece cannot be bought from a second browser (including by forcing the add-to-cart URL); pay-at-boutique checkout ends *Reserved* and shows so to others; removing from the cart releases the piece; sold pieces cannot be bought; enquiry validation and sending; phone menu with Escape; staff sign-in, dashboard and piece fields.
- **Not measured:** Lighthouse or real-world loading speed on your host. The theme loads one CSS file (~40 KB uncompressed), one deferred script (~3 KB), two preloaded fonts and no jQuery of its own (WooCommerce still loads its scripts on cart and checkout).

## Removed from the previous site

Style finder quiz, background music, scrolling brand band, the "six icons" design-reference photos (not Label Trunc's stock) and their notes, Instagram-post builder, WhatsApp message builder, on-device shortlist. The earlier Next.js version of this rebuild remains in the branch history (commit `1dca53a`) but is no longer used.
