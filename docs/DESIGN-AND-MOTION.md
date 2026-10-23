# Design and motion

Direction: **“Room to Flourish”** (master brief) in the interaction language of the design research report: *editorial clarity, spatial motion, purposeful content*. The school is made understandable before the interface is made spectacular: calm editorial layouts carry the information, and three signature scenes are separated by calm content.

## Design tokens

Defined once in [`src/styles/tokens.css`](../src/styles/tokens.css).

### Colour

Brand colours are the exact values declared in the official `isp-2019-logo.svg`.

| Token | Value | Use |
| --- | --- | --- |
| `--c-violet` | `#211646` | Primary actions, headings, ink sections (16.56:1 with white) |
| `--c-violet-900` | `#170f33` | Hover / pressed primary |
| `--c-purple` | `#9F26B5` | Eyebrows, focus ring, Secondary accent (6.14:1 with white) |
| `--c-gold` | `#F7B334` | Early Years accent, announcement bar (dark text only), focus ring on violet |
| `--c-lime` | `#82BC00` | Primary accent (graphic use only; never white text) |
| `--c-burgundy` | `#AC162C` | Error text and borders |
| `--c-text` / `--c-text-muted` | `#252134` / `#5D5968` | Body / secondary text |
| `--c-tint`, `--c-tint-strong` | `#F4F2F8`, `#EBE7F2` | Alternate section backgrounds (cool violet tint; no cream) |
| `--c-line`, `--c-line-strong` | `#E6E0EC`, `#CFC6DA` | Hairlines, borders |

Stage accents: Early Years gold, Primary lime, Secondary purple, Sixth Form violet. Colour is always paired with a text label.

### Type

- **Quicksand 600/700** (the school’s existing typeface) for headings and display.
- **Inter 400–600** for body text and interface.
- Both are self-hosted at build time by `next/font`; no requests to Google at runtime.

| Token | Size |
| --- | --- |
| `--fs-display` | 40 → 104px |
| `--fs-h1` | 36 → 72px |
| `--fs-h2` | 30 → 56px |
| `--fs-h3` | 20 → 28px |
| `--fs-lead` | 18 → 22px |
| `--fs-body` | 16 → 18px, line height 1.6 |
| `--fs-meta` | 13px minimum |

Headings use `text-wrap: balance` and a maximum width in their own `ch` units, so lines break at readable lengths.

### Layout and shape

- Container 1440px; gutters 20 → 64px; gaps 16 → 24px; section spacing 48 → 104px.
- 4px spacing scale (`--s-1` … `--s-10`).
- Radii: 8px small, 12px buttons/cards, 16–20px photographs. Pills only for chips.
- Shadows only on raised layers (menus, dialogs, cookie banner).

## Components

| Area | Components |
| --- | --- |
| Shell | Sticky header with utility row, click-to-open mega menu, mobile menu dialog, search dialog, announcement bar (dismissible), footer, mobile “Visit & Enquire” bar, breadcrumbs, skip link |
| Primitives | `Button` (primary, secondary, light; joined icon-label), `ArrowLink` (text roll + arrow swap), `CmsLink`, `SmartLink`, `Media` (responsive WebP, focal point, preload), `Section`, `SectionHeader`, `Dialog` (native `<dialog>`), `RichText` |
| Consent | `ConsentProvider`, `CookieBanner` with preferences dialog, `Analytics` (consent-gated), `ConsentMap` |
| Blocks | 25 section types, see the editor guide |

## Interaction language (motion level 1, 150–300ms)

One response per element type, shared by hover and keyboard focus:

- **Links:** the label rolls up to a duplicate (hidden from assistive technology) and the arrow is replaced by a second arrow.
- **Buttons:** colour deepens; joined icon cell nudges 3px.
- **Cards and photos:** image scales to at most 1.03; border or shadow emphasis.
- **Focus:** 3px purple ring with offset (gold on deep violet).

## Motion trigger table

| Scene | Where | Trigger and sequence | Values | Fallback |
| --- | --- | --- | --- | --- |
| **Spiral photo gallery** (M01) | Home hero | Photos sit on a shallow helix that turns as the page scrolls (one photo per 160px while the hero is on screen; scroll is read, never intercepted), by horizontal drag (8px threshold) and by arrow keys when the gallery is focused. No visible buttons. | Helix radius 50% of stage, 36° per photo, rise 3%; time-based damping (90ms); cards away from the front dim and desaturate; pointer tilt ≤2° on fine pointers | Under 768px a tighter helix; reduced motion: scroll does not turn it, keys and drag change photos instantly; every photo is in a semantic list with alt text and a live caption |
| **Horizontal learning journey** (M02) | Home | Native vertical scroll inside a wrapper of height `100vh + D`; `D = track.scrollWidth − viewport.clientWidth`; `x = −D × progress`. Counter, progress bar and “Skip the learning journey” link. Tabbing to a hidden panel scrolls it into view. | No easing on the track (position follows the scroll exactly) | Below 1024px wide or 680px tall, or with reduced motion: an ordinary vertical stack of the same panels |
| **Values spiral** (M04) | Vision & values (Terms block, *Spiral* display) | Section pins; vertical scroll turns a helix of term cards one at a time (0.5 viewport heights each), then releases. Counter, progress bar and Skip link. Cards show icon, term and tagline only. | 30° per card, damping 110ms | Below 900px wide or 640px tall, or reduced motion: a native sideways swipe row (keyboard-focusable) |
| **Sideways chapter** (M05) | Link cards and Numbered steps with *Sideways row* layout (Discover, Admissions, Community) | Same model as M02: `D = track.scrollWidth − viewport.clientWidth`, `x = −D × progress`; alternate cards drift ±16px vertically for depth. | No easing on the track | Below 1024px wide or 640px tall, or reduced motion: native swipe row with scroll snap |
| **Kinetic values** (M03) | Home | Section with ~1.1 extra viewport heights of scroll travel. Progress 0–0.15 bunched, 0.15–0.5 separate, 0.5–0.75 move outward (≤20vw; 12vw on phones), settle by 0.9 into a staggered composition. The moving words are the real heading text, read once in order. Decorative “@” rotates slightly. | Outer words never below 62% opacity (contrast stays above 3:1 for large text) | Reduced motion: the settled composition, no extra scroll height |
| **Reveals** (M06) | Section headings, cards, photos below the fold | IntersectionObserver marks elements once as they enter. | Rise 18px and fade over 560ms; photos scale from 1.035; 70ms stagger | Content is visible without JavaScript; reduced motion shows everything immediately; the first hero never animates (fast LCP) |
| **Curtain reveal and parallax** (M06b) | Feature photos (Image and text, Feature mosaic) and page-hero photos | Feature photos are uncovered upward once on entry; large photos drift against the scroll by ±28–48px (`data-parallax`, enlarged 12% so edges never show). | Curtain 900ms; parallax linear | Reduced motion: no curtain, no parallax |
| **Heading words and drift** (M06c) | Section headings, side headings beside long text, page titles | Words rise one at a time (55ms stagger) the first time a heading enters. While scrolling, headings glide in from one side through the lowest 45% of the screen and slide out to the other side once they leave through the top; sticky side headings ride down with the text and only slide out when their section ends. Alternate headings move in opposite directions. | Enter ≤8vw, exit ≤14vw; page width is clipped so nothing scrolls sideways | Reduced motion: static headings, no word reveal |
| **Read more and scroll cue** (M06d) | Text sections over 1,100 characters; home hero | Long text opens at 26rem with a fade and a round down-arrow *Read more* button (`aria-expanded`); the home hero has a bouncing *Scroll to discover* arrow linking to the next section. | Arrow nudge 1.8–2s loop | Reduced motion: arrows still, everything else unchanged; without JavaScript all text shows |
| **Page life** (M06e) | Every page | Reading-progress line along the top edge; content settles 14px on load; cross-document view transitions fade between pages where supported. | Enter 700ms; transitions 260/420ms | Reduced motion: none |
| **Word band** (M07) | Discover | Runs only while on screen; pauses on hover, focus and with its pause button. | 22px/s; duplicate track `aria-hidden` | Reduced motion: one static line |
| **Overlays** (M08) | Menu, search, cookie settings, gallery viewer | Native modal dialog: inert background, focus contained, Escape closes, focus returns to the opener, page scroll locked. | 320ms fade and 12px rise | Reduced motion: instant |

All scroll-driven scenes subscribe to one shared, `requestAnimationFrame`-throttled scroll listener ([`src/components/motion/scroll.ts`](../src/components/motion/scroll.ts)). There is no smooth-scroll library and no wheel interception. Reduced-motion changes are picked up live.

## Responsive behaviour

| Width | Behaviour |
| --- | --- |
| ≥ 1360px | Full navigation with mega-menu panels and utility row (labels never wrap) |
| 1024–1359px | Menu button; horizontal journey still active if the window is at least 680px tall |
| 768–1023px | Stacked journey; two-column grids |
| < 768px | Single column; sticky bottom “Visit & Enquire” bar (space reserved so nothing is covered); compact announcement; simplified explorer |

Tested at 320, 390, 412 (Pixel 7), 768, 1024, 1440 and 1920px, with no horizontal overflow on any page.
