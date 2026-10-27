# Component map and motion

## Design tokens

All colours, type, spacing, radii and motion values are in `src/styles/tokens.css`, named by role (`--primary`, `--muted`, `--accent`, `--line`…) and filled from the brand: LT wine `#501C2B`, cream `#F4EDE3`, gold. Display type is **Tinos** (the open, metric-compatible equivalent of the brand's Times New Roman wordmark); body type is **Inter**. Both are SIL Open Font License fonts, downloaded at build time and served from the site.

| Token | Value |
| --- | --- |
| Body text on cream | `#2A1B20`, 14.2:1 |
| Muted text on cream | `#5E4B51`, 7.0:1 (6.1:1 on sand bands) |
| Wine (buttons, links) on cream | 11.7:1 |
| Gold eyebrows on cream | `#7A5D26`, 5.3:1 (4.7:1 on sand bands) |
| Section spacing | 48 → 104 px (`--section`) |
| Container / gutters | 1440 px / clamp(20 px, 4vw, 64 px) |
| Radius | 8 / 12 / 18 px |
| Buttons | at least 44 px tall (48 px default) |

## Pages and their parts

| Route | Built from |
| --- | --- |
| `/` | `app/(frontend)/page.tsx`: hero (headline, two actions, real photo or address tag card, scroll cue), facts strip, latest stock (`ProductCard`), how buying works (steps from shop settings), `LitStatement`, about + consignment, visit band |
| `/shop` | filters (GET form, works without JavaScript), `ProductCard` grid, pagination |
| `/shop/[slug]` | `Gallery`, `AddToCartForm`, details list, reassurance strip, Product JSON-LD |
| `/cart`, `/checkout` | `HoldCountdown`, `CheckoutForm` (server action `checkoutAction`) |
| `/order/[number]` | status panel per order state, Billplz redirect verification, cancel |
| `/account/*` | sign in, register, forgot/reset password, orders, profile |
| `/about`, `/info`, policies | `[slug]/page.tsx` + `RenderBlocks` (content, terms, faq, imageText, notice, cta, latestStock) |
| `/visit`, `/contact` | site settings; `ContactForm` (server action `enquiryAction`) |
| `/admin` | Payload editor, branded with `components/admin/Logo` and `Welcome` |

Icons: [Lucide](https://lucide.dev/icons) via `lucide-react` (pinned), wrapped by `components/Icon.tsx` so pages use short names (`<Icon name="bag" />`) and only the icons used are shipped. Lucide has no brand logos, so social links use generic Lucide icons (WhatsApp → message-circle, Instagram → camera, TikTok → video, Facebook → users) and always show the handle as text. To change an icon, swap the import in `Icon.tsx`.

Site chrome: `components/site/Header.tsx` (sticky; the menu button replaces the links below 1100 px), `HeaderNav.tsx` (native `<dialog>` mobile menu), `Footer.tsx`, `Wordmark.tsx`.

## Motion trigger table

One scroll listener at most per page (only the lit statement uses one), throttled with `requestAnimationFrame`. Native scrolling only. Content never depends on an effect: everything is visible without JavaScript.

| Effect | Trigger | Values | Fallback (no JS, reduced motion) |
| --- | --- | --- | --- |
| Lit statement (home: "Curated. Collected. Consigned.") | Section holds (sticky) while the page scrolls through 200vh | Words brighten in turn between 10% and 80% of travel; muted state ≥4.5:1; gold underline scaleX 0→1 | All words lit, not pinned (also below 560 px tall) |
| Heading/step reveal (`data-reveal`) | Once, when scrolled into view | Opacity 0→1, rise 0.4em, 900 ms, ease (.22,1,.36,1) | Shown immediately; never applied to content already on screen at load |
| Scroll cue | Under the hero | 44 px round arrow nudges 4 px every 2.4 s | Static |
| Links, buttons, nav underline | Hover = focus | 180–280 ms colour/underline, arrow nudges 3 px | Colour and underline only |
| Product card image | Hover | scale ≤ 1.03, 560 ms | None |
| Mobile menu | Open | Native modal dialog, 320 ms fade + 12 px rise; Escape closes; focus returns | Instant |
| Hold countdown | Every 30 s | Text update in a fixed-width slot | Static time from the server |

Avoided on purpose (per the brief): moving headings with scroll, clip-path curtains, page transitions, multi-hue progress bars, autoplay without pause, custom cursors, WebGL.
