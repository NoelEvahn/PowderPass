# Label Trunc: website, online shop and editor

The website for **Label Trunc**, a boutique for new and pre-loved designer bags at Ukay Boulevard, Ampang.

| For visitors | For staff (`/admin`) |
| --- | --- |
| What Label Trunc is, where it is, buying information and policies | Add stock with photos; save as draft, preview, publish, mark sold |
| Contact form, WhatsApp, phone and email | Orders: see, mark paid / completed / cancelled |
| Shop with search and filters; only real stock is shown | Holds: see which pieces are in carts; release one |
| Cart that **holds a piece for up to 24 hours** | Enquiries from the contact form |
| Checkout: **pay online** (FPX online banking: Maybank2u, HSBC, PBe and other banks; cards; Touch 'n Go eWallet and other e-wallets via Billplz) or **reserve and pay at the boutique** | Edit pages (About, Buying info, policies) from reusable sections |
| Customer accounts (order history); guest checkout also works | Contact details, opening hours, banner, shop settings |

Staff instructions are in [docs/EDITOR-GUIDE.md](docs/EDITOR-GUIDE.md). Facts and their sources are in [docs/SOURCES.md](docs/SOURCES.md). What is unfinished or unconfirmed is in [docs/DISCLOSURE.md](docs/DISCLOSURE.md).

## Stack

- **Next.js 16** (App Router, React 19) with **Payload CMS 3** embedded: one app serves the site, the editor at `/admin` and the API.
- **PostgreSQL** for all data (any provider).
- **Photos** on local disk (default) or any **S3-compatible** storage. Payload creates 400/800/1400/2200 px WebP sizes.
- **Billplz** for online payments (optional; without it the shop runs on reserve-and-pay-at-the-boutique).
- **SMTP** for email (optional; without it emails go to the server log).
- Plain CSS with design tokens in `src/styles/tokens.css`; fonts are self-hosted at build time.

## Run it locally

Requirements: Node 22.12+, pnpm 10, PostgreSQL 14+.

```bash
pnpm install
cp .env.example .env            # set DATABASE_URL, PAYLOAD_SECRET, SERVER_URL=http://localhost:3000
echo "PAYLOAD_DB_PUSH=true" >> .env   # dev only: keep the schema in sync automatically
SEED_ADMIN_EMAIL=you@example.com SEED_ADMIN_PASSWORD='a-long-password' pnpm seed
pnpm dev                        # http://localhost:3000 and http://localhost:3000/admin
```

`pnpm seed` adds the facts already published on the old site (address, phone, email, social links), the home page text, the editable pages and the first administrator. It **never creates stock or images**. It is safe to run again.

## Hosting

The app is a standard Node server, so it runs on any host. Two tested paths:

### Option A: managed (Vercel + Neon + R2 or S3)

1. Create a Postgres database (for example on Neon) and copy its connection string.
2. Create an S3-compatible bucket (Cloudflare R2, AWS S3, Backblaze B2) and an access key. Vercel's disk is temporary, so photos **must** go to a bucket here.
3. Import the repository into Vercel and add the environment variables from `.env.example`: `DATABASE_URL`, `PAYLOAD_SECRET`, `SERVER_URL`, the `S3_*` values, and optionally Billplz, SMTP and `CRON_SECRET`.
4. Deploy. Database migrations run automatically when the server starts.
5. Visit `/admin` and create the first administrator (or run `pnpm seed` locally against the production `DATABASE_URL`).
6. Add a scheduled call to `/tasks/release-holds` (see below). On Vercel, use Vercel Cron, or an external scheduler such as cron-job.org, with the header `Authorization: Bearer <CRON_SECRET>`.

### Option B: your own server (Docker)

```bash
cp .env.example .env    # fill in PAYLOAD_SECRET, SERVER_URL, optional Billplz/SMTP
echo "POSTGRES_PASSWORD=$(openssl rand -hex 16)" >> .env
docker compose up -d --build
```

This starts the app, PostgreSQL and a persistent volume for photos. Put a reverse proxy with HTTPS in front of it (Caddy, Nginx, Traefik). Back up the `pgdata` and `media` volumes. Add a cron entry for holds, for example:

```cron
*/10 * * * * curl -fsS -H "Authorization: Bearer $CRON_SECRET" https://www.example.com/tasks/release-holds
```

### Environment variables

See `.env.example`; every variable is described there. Required: `DATABASE_URL`, `PAYLOAD_SECRET`, `SERVER_URL`.

### Database migrations

The schema lives in `src/migrations` and is applied automatically in production when the server starts. After changing a collection or field:

```bash
pnpm migrate:create   # writes a new migration from the change
pnpm migrate          # applies it (also happens on deploy)
```

Never set `PAYLOAD_DB_PUSH=true` in production.

## Online payments (Billplz)

1. Open a Billplz account (use [billplz-sandbox.com](https://www.billplz-sandbox.com) first to test).
2. Create a **Collection**. In its payment settings, enable FPX online banking, cards, and the e-wallets you want (for example Touch 'n Go eWallet).
3. In Settings, copy the **API Secret Key**, the **Collection ID** and the **X Signature Key**, and enable X Signature.
4. Set `BILLPLZ_API_KEY`, `BILLPLZ_COLLECTION_ID`, `BILLPLZ_X_SIGNATURE_KEY` (and `BILLPLZ_SANDBOX=true` while testing).
5. Redeploy. The checkout now offers "Pay online now". Payment confirmations arrive at `/payments/billplz/callback`, which is signature-checked.

How orders work:

- **Pay online:** order status *Awaiting online payment*. The piece stays held while the customer pays (at least 30 minutes, configurable). Billplz confirms, and the order becomes *Paid* and the piece *Sold*.
- **Reserve and pay at the boutique:** order status *Reserved*, piece held for 24 hours (configurable). Staff mark the order *Paid* in the editor when the customer pays, which marks the piece *Sold*.
- Unpaid holds end automatically and the piece returns to the shop. A payment that arrives after a hold ended still completes if the piece is free; otherwise the order is flagged *Refund due* and staff get an email.

## Holds and the scheduled task

Holds end on their own whenever someone uses the shop. To send "your hold has ended" emails on time when the site is quiet, call `GET /tasks/release-holds` with `Authorization: Bearer <CRON_SECRET>` every 5 to 15 minutes.

## Security

- No public staff sign-up. Staff sessions last 8 hours. Five failed sign-ins lock an account for 15 minutes (staff and customers).
- Roles: **Administrator** (everything, staff, shop settings), **Editor** (stock, pages, images, contact details), **Sales** (orders, enquiries, holds, stock status). The last administrator cannot be demoted or deleted.
- Orders, enquiries, holds and customers are never readable through the public API. Customers can read only their own orders. Guest order links carry a secret token.
- Forms are validated on the server, rate-limited, and protected from spam without a CAPTCHA (hidden field and minimum fill time).
- Security headers: Content Security Policy, `frame-ancestors 'self'`, `nosniff`, referrer and permissions policy, and HSTS when the site is served over HTTPS.
- No analytics, advertising or tracking. Only two cookies: the sign-in session and the cart.
- To restrict `/admin` further (for example to the boutique's network), add an IP allow-list for `/admin` at your reverse proxy or host.

## Tests

```bash
pnpm typecheck
pnpm lint
pnpm test:int      # unit tests and integration tests (uses a separate database: TEST_DATABASE_URL, default postgres://lt:lt@localhost:5432/labeltrunc_test)
pnpm test:e2e      # Playwright + axe against a running production build (see tests/e2e/README.md)
```

## Project map

```
src/
  app/(frontend)/        public pages, server actions (actions/)
  app/(payload)/         the editor (/admin) and REST API (/api) — generated by Payload
  app/payments/…         Billplz callback
  app/tasks/…            scheduled hold release
  collections/, globals/ content model (stock, orders, holds, pages, settings…)
  blocks/                page sections editors can add
  components/            UI (site chrome, shop, forms, blocks, admin branding)
  lib/commerce/          holds, checkout, payments, order status, emails
  styles/                tokens.css (design tokens) + component styles
  seed/                  real facts and pages, no stock
docs/                    editor guide, sources register, disclosure, component & motion map
```
