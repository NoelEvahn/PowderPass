# Editor guide (WordPress)

Everything is edited in **wp-admin** (`https://yourdomain/wp-admin`). Sign in with your staff account.

> Five wrong passwords in a row lock that sign-in for 15 minutes. Staff accounts are added by an administrator under **Users → Add New**.

## Who can do what

| Task | Administrator | Shop manager |
| --- | :-: | :-: |
| Add, edit and publish pieces; upload photos | ✓ | ✓ |
| Orders, reservations, refunds, enquiries | ✓ | ✓ |
| Pages, menus, contact details, home page text (Customizer) | ✓ | ✓ |
| Hold rules (WooCommerce → Settings) | ✓ | ✓ |
| Plugins, themes, Billplz keys, users, Tools → Label Trunc setup | ✓ | |

Give day-to-day staff the **Shop manager** role. Keep administrator accounts few, with strong passwords.

## Your dashboard

**Dashboard → Label Trunc today** shows what needs you: refunds due, orders to prepare, reservations waiting for collection, unpaid checkouts, new enquiries, pieces in carts, and pieces for sale and sold. Each line links to the list.

## Add a piece

**Products → Add New.**

1. **Name**: what a customer would search for, e.g. "Kelly 28 Sellier, Togo leather".
2. **Description** (the big box): anything a buyer should know. Optional.
3. **Product data → General → Regular price** in RM (whole ringgit). Use *Sale price* only for a real reduction.
4. **Product data → Piece details**: **Condition** (required), *Condition notes* (marks, wear, repairs), *Comes with* (box, dust bag, receipt…), colour, material, hardware, size, year.
5. **Brands** box (right): the designer. **Product categories**: *Top handle*, *Shoulder*, or your own.
6. **Product image** (right): the main photo. **Product gallery**: more photos. Fill in each photo's *Alternative text* (e.g. "Front of the black Kelly 28, gold hardware").
7. **Publish.**

The site does the rest automatically:

- Stock is set to exactly 1 and the piece can only be bought once.
- A stock number (`LT-0042`) is given if the SKU is empty. Write it on the piece's tag.
- A piece **without a photo, price or condition stays a draft**, with a message telling you what is missing.

Photos: JPG, PNG or WebP up to 15 MB, ideally 1600 px or wider, portrait (4:5) on a plain background. They are cropped to 4:5 in the shop.

## A piece sells in the boutique

Open the piece, **Inventory → Stock quantity → 0**, Update. It shows *Sold* everywhere at once.

If someone had it in their cart, it is removed from their cart. If someone was half-way through paying online, their order is cancelled and they get an email. If their payment still goes through, the order is marked **Refund due** (see below) and you get an email, so nobody is ever sold the same piece twice.

Sold pieces stay visible as *Sold* for 30 days (change in hold rules), then hide from the shop automatically. Their pages stay up for anyone with the link.

## Holds and reservations: what customers see

| Status on the site | Meaning |
| --- | --- |
| *Available* | Anyone can add it to their cart |
| *In your cart* | This visitor holds it |
| *On hold* | In another shopper's cart until the time shown; nobody else can buy it |
| *Awaiting payment* | Another shopper is paying online right now |
| *Reserved* | Reserved online to pay at the boutique |
| *Sold* | Gone |

The **Availability** column in *Products* shows the same for staff, with the order number when there is one.

## Orders

**WooCommerce → Orders.**

| Status | What to do |
| --- | --- |
| **Processing** | Paid online. Prepare the piece; mark **Completed** when collected or delivered. |
| **Reserved (pay at boutique)** | The customer will come in. Take payment, then mark **Completed**. If they don't come, it cancels itself when the reservation time ends and the piece goes back on sale. You can also cancel it yourself. |
| **Pending payment** | Checkout started, not paid. Cancels automatically after the payment window (30 minutes by default) after one last check with Billplz. |
| **Refund due** | Money arrived for a piece someone else already bought. **Refund the customer in Billplz** (or by bank transfer), contact them, then mark the order **Refunded**. |
| **Cancelled / Refunded** | Done. |

### Refunds and returns

Refunding an order **never puts the piece back on sale by itself**, because a refunded piece may still be with the customer or need checking. The *Restock refunded items* box starts unticked; leave it.

When the piece is physically back and checked: open the order → **Order actions → Return pieces to stock (piece is back in the boutique)** → Update. It goes back on sale and a note is added.

## Enquiries

**Enquiries** in the menu lists messages from the contact form (newest first), with name, topic and the piece they asked about. Set the status to *Replied / in progress* or *Closed* as you go. Reply by email, phone or WhatsApp from the details shown. A copy of each new enquiry is also emailed to the address set in the Customizer.

## Contact details, hours and home page text

**Appearance → Customize → Label Trunc**:

- **Contact**: phone, WhatsApp number (digits with country code, e.g. 60124211205), email, where enquiries are sent, address, Google Maps link, opening hours (one line per row: `Mon – Sat | 11am – 7pm`; leave empty to show "Appointments recommended…").
- **Social**: Instagram, TikTok, Facebook links.
- **Home page**: hero heading and intro, hero photo (optional; otherwise the newest piece's photo is used), the statement, About and Consignment text, visit photo.
- **Business**: registered name and SSM number for the footer.

Changes preview live; press **Publish**.

## Pages and policies

**Pages** holds About, Buying info, Visit, Contact, Privacy (and Privasi in Malay), Cookies, Terms, Refunds, Accessibility and Business details. Edit them like any document.

Policy pages imported with **[bracketed]** placeholders show visitors a notice that parts await confirmation. Replace every bracketed item, then untick **Parts of this page await confirmation** (right side of the editor) and Update.

Menus are under **Appearance → Menus** (*Label Trunc main* and *Label Trunc policies*).

## Hold rules

**WooCommerce → Settings → Products → Label Trunc holds**:

| Setting | Default | Allowed |
| --- | --- | --- |
| Cart hold for signed-in customers | 24 h | 1–24 h |
| Cart hold for guests | 24 h | 1–24 h. **2–4 h is recommended** so anonymous visitors cannot sit on pieces |
| Pay-at-boutique reservation | 24 h | 1–168 h |
| Online payment window | 30 min | 10–120 min |
| Pieces per cart | 3 | 1–10 |
| Holds per visitor (all carts from one connection) | 5 | 1–30 |
| Keep sold pieces visible | 30 days | 0–365 |

Holds never renew silently: when the time is up, the piece leaves the cart and goes back on sale.
