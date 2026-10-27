# Editor guide

Everything on the website is edited at **`/admin`** (for example `https://www.labeltrunc.com/admin`). Sign in with your staff email and password.

> Five wrong passwords lock the account for 15 minutes. You stay signed in for 8 hours. Only an administrator can add staff; there is no public sign-up.

## Who can do what

| Task | Administrator | Editor | Sales |
| --- | :-: | :-: | :-: |
| Add, edit, publish and delete stock | ✓ | ✓ | edit only (e.g. mark Sold) |
| Upload and describe photos | ✓ | ✓ | ✓ |
| Edit pages, home page, contact details, hours, banner | ✓ | ✓ | |
| See and update orders, holds, enquiries, customers | ✓ | | ✓ |
| Shop settings (holds, payment options, delivery) | ✓ | | |
| Add or remove staff, change roles | ✓ | | |

The last administrator cannot be demoted or deleted, so you can never lock yourselves out.

## Add a piece to the shop

1. Dashboard → **Add a piece** (or *Stock → Create new*).
2. **Piece** tab: title (e.g. "Classic Flap Medium, black caviar"), designer/brand (press **+** to add a new brand), category, **price in RM**.
3. **Photos**: drag photos in, or *Create New*. Use real photos of this exact piece. For each photo, write a **description** of what it shows (for people using screen readers), for example "Black quilted leather bag, front view, gold chain". The first photo is the cover; drag to reorder. Large photos are resized automatically.
4. **Condition & details** tab: condition grade, honest notes on wear, what it comes with, colour, material, hardware, size, year.
5. Right-hand side:
   - **Can be bought online**: untick to show the piece as "In the boutique, ask us" with no cart button.
   - **Show on home page**: featured pieces appear first in "New in the boutique".
6. Your work saves automatically as a **draft**. Press the eye icon to **preview** it exactly as customers will see it (phone, tablet and desktop sizes are available in *Live preview*).
7. Press **Publish changes**. It is now in the shop.

**Changing a piece later:** edit and press *Publish changes*. Until you publish, customers keep seeing the previous version. *Versions* (top right) shows every saved version and lets you restore one.

**Sold in the boutique?** Set **Status → Sold** and publish. Sold pieces stay visible marked "Sold" for 30 days (change this in *Shop settings*), then disappear from the shop. **Archived** hides a piece completely without deleting it.

> Opening "Add a piece" creates an empty draft straight away. If you leave without filling it in, delete it from the Stock list. Drafts are never shown to customers.

## Orders

*Shop → Orders* lists every order, newest first. You get an email for each new order (sent to the notification email in *Contact & business details*).

| Status | Meaning | What you do |
| --- | --- | --- |
| Awaiting online payment | The customer is paying on Billplz. The piece is held. | Nothing. It becomes **Paid** by itself, or **Expired** if they do not pay. |
| Reserved: pay at boutique | The customer will come in to pay and collect. The piece is held until the time shown. | When they pay, set the status to **Paid**. |
| Paid | Payment received. The piece is marked **Sold**. | Arrange collection or delivery. Then set **Completed**. |
| Completed | Collected or delivered. | Nothing. |
| Cancelled / Expired | No sale. The piece is back in the shop. | Nothing. |
| Refund due | A payment came in after the hold ended and the piece had gone, or the amount was wrong. | Refund the customer in Billplz and contact them. |

- To **extend a hold**, change *Piece held until*.
- **Cancelling** a paid order puts the piece back in the shop (for example after a refund).
- Every change to an order is recorded under *Versions*.

## Holds

*Shop → Holds* shows pieces currently in someone's cart or held by an unpaid order. Holds end by themselves (24 hours for carts at most). To release one now, delete it.

## Enquiries

Messages from the contact form appear in *Shop → Enquiries*, and are emailed to you. Set the status to *Replied / in progress* or *Closed* to keep track.

## Pages

*Content → Pages*: About, Buying info and the policy pages. Each page is built from **sections** you can add, drag to reorder, duplicate or remove:

| Section | Use it for |
| --- | --- |
| Text | Paragraphs, headings, lists, links |
| Icon list | Short promises or features: icon, a few words and one line |
| Questions & answers | FAQs (also helps Google show answers) |
| Photo and text | A real photo beside some text |
| Notice | An information box, or "Awaiting confirmation" |
| Call to action | A wine-coloured band with up to two buttons |
| Latest stock | Shows the newest pieces automatically |

Tick **Parts await confirmation** on a page to show a visible notice while wording is being checked. Text in [square brackets] on the policy pages is waiting for Label Trunc to confirm; please replace it with the confirmed wording and untick the box.

## Home page

*Content → Home page*: the headline, the introduction, an optional **hero photo** (a real photo of the boutique or your pieces; without one, an address card shows), the facts strip, and the About and consignment texts.

## Contact & business details

Phone, WhatsApp number (digits only, e.g. `60124211205`), email, social links, address, Google Maps link, **opening hours** (leave empty until confirmed, and the site then says appointments are recommended), registered name and SSM number, and the **announcement banner** (with an optional end date).

## Shop settings (administrators)

- **Cart hold**: up to 24 hours.
- **Pay-at-boutique hold**: how long a reservation is kept before it returns to the shop.
- **Minimum time to pay online**.
- **Most pieces one cart can hold** (stops one person holding the whole shop).
- **Payment options**: online payment (needs Billplz keys, see the README) and/or reserve and pay at the boutique.
- **Delivery**: turn on, set a fee and a note. Delivery is only offered with online payment.
- **Keep sold pieces visible for** a number of days.

## Restricting access to `/admin`

`/admin` is protected by sign-in, lockout and roles. To restrict it further, ask your host to allow `/admin` only from the boutique's internet connection (an IP allow-list), or add single sign-on in front of it.
