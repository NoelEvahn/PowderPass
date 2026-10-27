# Disclosure: what is unfinished, unconfirmed or untested

Read this before launch. It is kept honest on purpose.

## Needs Label Trunc's input before launch

| Item | Where | What to do |
| --- | --- | --- |
| Real stock and photos | Shop | Add pieces in the editor. The shop is empty until then and says so honestly. |
| Opening hours | Contact & business details | Enter them, or leave empty to keep "appointments recommended". |
| Registered business name and SSM number | Contact & business details; Business details page | Enter them; replace the [bracketed] placeholders on the policy pages. |
| Policy wording | Privacy (EN and BM), Cookies, Terms, Refunds, Accessibility, Business details | Each page shows "awaiting confirmation". Replace every [bracketed] item, then untick *Parts await confirmation*. Have the refund and terms text checked by someone qualified. |
| Condition grade definitions | Buying info FAQ | Confirm or edit the wording. |
| Delivery | Shop settings | Off by default. Turn on with a fee and note when confirmed. |
| Billplz account | Server settings | Without keys, only "reserve and pay at the boutique" is offered at checkout. Test with the sandbox first. If you launch before Billplz is ready, also edit the *Buying info* page and the home page facts strip, which describe online payment. |
| SMTP email | Server settings | Without it, order, password-reset and staff emails are only written to the server log. |
| Hosting, domain, HTTPS, backups | — | See README. Then fill in the hosting provider on the privacy page. |
| Hero photo | Home page | Optional. Until a real photo is added, the address tag card shows. |

## Built but not verifiable here

- **Billplz live payments.** The integration follows Billplz's v3 API documentation and is tested against a local mock server (bill creation, signature verification, callbacks, refunds-due logic). It has **not** been run against a real Billplz sandbox account, because no credentials were available. Do one full sandbox payment (FPX and Touch 'n Go) before going live.
- **Email delivery.** Templates are tested to render and send through Payload; no real SMTP server was used.
- **Real devices.** Tested in Chromium (Playwright) at phone, tablet and desktop widths. Not yet checked on a physical iPhone (Safari) or Android phone, or with VoiceOver and TalkBack.
- **Multiple servers.** The form rate limiter and payment-confirmation lock are per server process. On a single server (the normal setup) they are complete. If the site is ever scaled to several instances, the database unique index still prevents double-holding, but rate limits apply per instance.

## Test results at hand-over

- Type-check and lint: clean.
- Unit and integration tests (Vitest, real PostgreSQL): 45 passing, covering holds (including 12 simultaneous shoppers on one piece), checkout, Billplz signatures and callbacks against a mock, late and mismatched payments, order status changes, access control and the last-administrator rule.
- End-to-end (Playwright on a production build, desktop 1366 px and Pixel 7): 66 passing. They cover shop browsing and filters, the product gallery, add to cart, the hold seen from a second browser, reserve-and-pay-at-the-boutique checkout, cancel, accounts, the contact form, the editor sign-in, API privacy, internal link crawl, every image loading, axe (zero serious or critical issues on 19 pages at both sizes), no horizontal scroll at 320 to 1920 px, and reduced motion.
- Production server (`node server.js`, as in Docker): pages, editor, uploads and security headers checked; refuses to start serving without `PAYLOAD_SECRET`.

## Removed from the previous site

Style finder quiz, background music, scrolling brand-name band, "Six icons" design-reference photos (not Label Trunc's stock) and their design-history notes, Instagram-post link builder, WhatsApp message builder, and the on-device shortlist. The shop, cart and contact form replace them.
