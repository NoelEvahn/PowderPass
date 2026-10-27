# Design system and template map

## Direction

An editorial boutique, not a template. Principles taken from studying Linear and Aesop, without copying either:

| Principle | From | How it shows here |
| --- | --- | --- |
| One accent, used only for action | Linear | Burgundy `#501c2b` on buttons, focus rings, the wordmark's full stop. Never on decoration. |
| Structure from hairlines and space, not boxes | Linear, Aesop | 1 px rules (`--line`) separate rows; no cards, shadows, pills or rounded corners (radius 0 everywhere). |
| Asymmetric splits | Linear | 5/7 and 6/5 grids; a sticky heading beside a ledger of steps. |
| Two-tone statement | Linear | The home statement: lead phrase in ink, the rest in secondary ink. |
| Precise small metadata | Linear | Stock numbers (`LT-0042`) and prices in tabular figures. |
| Warm paper, quiet sans, editorial serif | Aesop | Cream canvas, Instrument Sans for reading, Newsreader for headings. |
| Product as object, few per row | Aesop | 3 pieces per row (2 on phones), 4:5 photos, second photo on hover. |
| Plain, specific language | Aesop | "Held for you until Wed 28 Oct, 12:46pm", never "Hurry!". |

Deliberately avoided (common AI-design tells): icon-in-circle feature grids, gradient blobs, pill badges, emoji, thick coloured left borders on notices, scroll-triggered fade-ins, "Elevate your…" copy, Inter as the default face, rounded cards with drop shadows, fake urgency or fake stock.

## Tokens (`assets/css/theme.css`, `:root`)

| Token | Value | Contrast on paper |
| --- | --- | --- |
| `--paper` | `#f7f2ea` | canvas |
| `--sand` | `#ede4d7` | tinted band |
| `--ink` | `#241a1d` | 15.2:1 |
| `--ink-2` | `#5b4a50` | 7.4:1 (6.6:1 on sand) |
| `--wine` | `#501c2b` | 12.2:1 |
| `--held` | `#8a5a12` | 5.3:1 |
| `--line` / `--line-2` | `#dcd0c0` / `#8f7b63` | decorative hairline / input border 3.6:1 |

Type: Newsreader (300–600, optical sizes) for headings, Instrument Sans (400–600) for text, Tinos Bold for the wordmark only. All SIL Open Font License, self-hosted in `assets/fonts`, two preloaded. Sizes are fluid (`clamp`), from `--t-display` down to `--t-meta`.

Layout: max width 1440 px, gutter `clamp(20px, 4vw, 56px)`, section spacing `clamp(56px, 7vw, 112px)`, header 64 px. Touch targets at least 44 px. Status is always words plus a small square mark, never colour alone.

Motion: only to show state: link underline retracts, colour shifts, a slow second-photo crossfade on hover. All of it is turned off under `prefers-reduced-motion`.

Icons: Lucide (ISC licence), inlined as SVG by `lt_icon()` in `inc/template-tags.php`. Social networks are text links because Lucide has no brand marks.

## Templates

| URL | Template | Parts |
| --- | --- | --- |
| `/` | `front-page.php` | hero (heading, intro, real photo or the luggage-label card), statement, newest pieces, how buying works (adapts to Billplz), about and consignment, `template-parts/visit-band.php` |
| `/shop/`, categories, search | `woocommerce/archive-product.php` | category tabs, toolbar form (search, designer, condition, order, recently sold; works without JavaScript), grid, pagination, honest empty states |
| Product card | `woocommerce/content-product.php` | photo (+ hover photo), designer, stock number, name, price, status |
| `/product/…` | `woocommerce/content-single-product.php` | swipeable gallery on phones, sticky panel (price, buy or status, ask by form or WhatsApp, facts ledger, condition notes, assurances), related pieces, phone buy bar |
| Pages | `page.php` | header, "awaiting confirmation" notice, block content in `.lt-prose` |
| `/visit/`, `/contact/` | `page-templates/visit.php`, `contact.php` | address/hours/phone ledger, map link; enquiry form `[lt_enquiry_form]` |
| Cart, checkout, account | WooCommerce classic templates | restyled in `theme.css` |
| 404, search fallback | `404.php`, `index.php` | |

Contact details and home page text come from the Customizer (`inc/customizer.php`, `lt_mod()`); nothing is hard-coded in templates.
