# Architecture

## Overview

```
                 ┌──────────────────────── Next.js app (one deployable) ────────────────────────┐
 Visitors ─────▶ │  (frontend) routes ── RenderBlocks ── block components ── lib/queries (cached) │
                 │        │                                                    │                │
                 │        └── server action: submitEnquiry ──┐                 │                │
 Staff ────────▶ │  (payload) /admin + REST /api ── Payload ─┴── access control, hooks ─────────┤
                 └───────────────────────────────────────────────────────────────┬─────────────┘
                                                                                 │
                                    PostgreSQL ◀─────────────────────────────────┤
                                    Media: ./media or S3 ◀───────────────────────┤
                                    SMTP / CRM webhook (optional) ◀──────────────┘
```

The public site and the editor run in the same Next.js process and use Payload’s Local API directly (no HTTP round trip), so the editor always manages the real public content.

## Content model

| Collection / global | Purpose | Drafts | Who can edit |
| --- | --- | --- | --- |
| `pages` | Every page: title, slug, parent → computed `path`, ordered `layout` of blocks, SEO, owner, review date | Yes (autosave, 50 versions) | Admin, editor, contributor (drafts only) |
| `stories` | News, announcements, achievements | Yes | Admin, editor, contributor (drafts only) |
| `events` | Open days and public events, in Malaysia time; hidden after they end | Yes | Admin, editor, admissions |
| `programmes` | The four learning stages used by the journey, search and menus | Yes | Admin, editor, contributor (drafts only) |
| `faqs` | Questions by category; deep-linkable | Yes | Admin, editor, admissions |
| `media` | Images and PDFs; WebP sizes 400/800/1400/2200 + 1200×630 OG; focal point; alt text; approval status; source and release reference | — | Staff |
| `reusable-sections` | Blocks edited once and placed on many pages | — | Admin, editor |
| `enquiries` | Form submissions; created only by the server action | — | Read/update: admin, editor, admissions |
| `users` | Staff accounts with roles | — | Admin (others: own account) |
| `site-settings` | School name, campuses, contacts, social links, announcement | — | Admin |
| `header`, `footer` | Navigation | — | Admin, editor |
| `admissions` | Academic year, fee schedule link, optional approved fee table, waiver, application link, documents | Yes | Admin, editor, admissions |

Blocks are defined in `src/blocks/<Name>/config.ts` (schema) next to `Component.tsx` (renderer). Adding a section type means adding both, registering them in `src/blocks/index.ts` and `src/blocks/RenderBlocks.tsx`, then running `generate:types` and `migrate:create`.

Each block’s database tables are prefixed with the parent table (`dbName: ({ tableName }) => …`), keeping names under Postgres’s 63-character limit without collisions between collections. Each collection receives its own copy of the block configs because Payload sanitises them in place.

## Security

- **Authentication:** Payload auth with HTTP-only cookies (`Secure` in production, `SameSite=Lax`), 8-hour tokens, lockout after 5 failed attempts for 15 minutes. No public sign-up.
- **Authorisation:** role checks in `src/access`. Contributors are blocked from publishing by a `beforeChange` hook; only admins can change roles; the last administrator cannot be demoted.
- **CSRF / CORS:** restricted to `NEXT_PUBLIC_SERVER_URL`.
- **Drafts:** public queries always filter `_status = published`; preview mode is enabled only after the preview route verifies an editor session, and it rejects off-site redirect targets.
- **Forms:** server-side validation with the same schema used in the browser; honeypot field; minimum completion time; per-IP rate limit (5 per 10 minutes; IP from `X-Real-IP` or the proxy-appended `X-Forwarded-For` entry, never the client-supplied first entry); enquiries cannot be created or read via the public API.
- **Headers:** Content Security Policy (only Google Analytics and Google Maps as third parties, both consent-gated), `frame-ancestors 'self'`, `X-Content-Type-Options`, `Referrer-Policy`, `Permissions-Policy`, HSTS in production, `X-Powered-By` removed.
- **Secrets:** environment variables only; nothing sensitive in client code. The GA measurement ID is validated before being written into a script.
- **GraphQL** is disabled to reduce the API surface.

## Caching and publishing

- Published reads are wrapped in `unstable_cache` with the tag `site` and a 1-hour safety revalidation (events: 5 minutes so past events drop off).
- Any publish, unpublish or delete (`hooks/revalidate.ts`) invalidates the `site` tag, so changes appear immediately.
- Drafts (preview mode) bypass the cache.
- After seeding outside the running app, restart the server so caches rebuild.

## Search

Pages, stories and FAQs keep a hidden `searchText` field, flattened from their blocks and rich text on save. `lib/search.ts` queries published documents, ranks title matches above body matches and builds excerpts. It powers both `/api/site-search` (header dialog, rate-limited) and the `/search` page (works without JavaScript).

## Enquiry workflow

1. `EnquiryForm` validates on blur and on submit (`lib/enquiry/schema.ts`, zod/mini).
2. The `submitEnquiry` server action re-validates, applies spam controls and rate limiting, then stores the enquiry.
3. Only after storage succeeds: `deliverEnquiry` sends the staff email (if SMTP is configured) and POSTs to the webhook (if configured), recording delivery status on the enquiry.
4. The visitor sees a reference number. If storage fails, they see an error, keep their answers and get an alternative email address.

**Webhook contract** (`EnquiryWebhookPayload` in `lib/enquiry/deliver.ts`): `POST` JSON with `event: "enquiry.created"`, `id`, `createdAt`, `type`, `name`, `email`, `phone`, `stage`, `intendedStart`, `message`, `consentUpdates`, `sourcePath`, `adminUrl`; optional `Authorization: Bearer <ENQUIRY_WEBHOOK_TOKEN>`; 5-second timeout.

## Extension points

| Need | Where |
| --- | --- |
| Admissions platform / CRM | `ENQUIRY_WEBHOOK_URL` or extend `deliverEnquiry` |
| School management system data (e.g. events, calendar) | A Payload job or endpoint that upserts into `events`; the public site needs no changes |
| Shared rate limiting across servers | Replace `lib/rate-limit.ts` (same signature) with Redis |
| Single sign-on for staff | Payload custom auth strategy on the `users` collection |
| Translations (e.g. Bahasa Malaysia, Mandarin) | Payload `localization` config; fields are already centralised |
| Analytics events | `trackEvent()` in `components/consent/Analytics.tsx`; currently records anonymous enquiry start/submit only |

## Testing

- `tests/unit` — schema, consent parsing, rate limiter, IP extraction, search text, slugs, redirects.
- `tests/e2e` — run against a seeded server; desktop and mobile projects; includes axe accessibility scans and CMS role checks through the REST API.
