import type { Metadata } from 'next'
import Link from 'next/link'
import { notFound } from 'next/navigation'
import React from 'react'

import { cancelOrderAction } from '@/app/(frontend)/actions/checkout'
import { Icon } from '@/components/Icon'
import { formatRM } from '@/lib/catalog'
import { extractRedirectParams, verifyRedirect } from '@/lib/commerce/billplz'
import { releaseExpired } from '@/lib/commerce/cart'
import { confirmOnlinePayment, findOrderForViewer } from '@/lib/commerce/orders'
import { getBillplzConfig } from '@/lib/env'
import { getPayloadClient } from '@/lib/payload'
import { getCustomer } from '@/lib/session'
import { addressLines, getSite, whatsappLink } from '@/lib/site'
import { formatDay, formatWhen } from '@/lib/when'
import type { Order } from '@/payload-types'

export const metadata: Metadata = {
  title: 'Your order',
  robots: { index: false, follow: false },
}

type SP = Record<string, string | string[] | undefined>
const one = (v: string | string[] | undefined) => (Array.isArray(v) ? v[0] : v)

const METHOD_LABEL: Record<Order['paymentMethod'], string> = {
  online: 'Online (FPX, card or e-wallet)',
  pay_at_shop: 'Pay at the boutique',
}

export default async function OrderPage({ params, searchParams }: { params: Promise<{ number: string }>; searchParams: Promise<SP> }) {
  const { number } = await params
  const sp = await searchParams
  const orderNumber = decodeURIComponent(number)
  const token = one(sp.t) ?? ''
  const payload = await getPayloadClient()
  const customer = await getCustomer()
  await releaseExpired(payload)

  let order = await findOrderForViewer(payload, { orderNumber, token, customerId: customer?.id })
  if (!order) notFound()

  // Returning from Billplz: confirm a verified, paid redirect without waiting for the callback.
  const billplz = extractRedirectParams(sp)
  const cfg = getBillplzConfig()
  let paymentNotCompleted = false
  if (cfg && Object.keys(billplz).length) {
    const valid = verifyRedirect(billplz, cfg.xSignatureKey)
    if (valid && billplz.paid === 'true' && billplz.id && billplz.id === order.payment?.billId) {
      await confirmOnlinePayment(payload, { billId: billplz.id, paidAt: billplz.paid_at, transactionId: billplz.transaction_id })
      const fresh = await findOrderForViewer(payload, { orderNumber, token, customerId: customer?.id })
      if (fresh) order = fresh
    } else if (valid && billplz.paid === 'false') {
      paymentNotCompleted = true
    }
  }

  const site = await getSite()
  const wa = whatsappLink(site, `Hi Label Trunc, a question about order ${order.orderNumber}`)
  const holdEnds = order.holdExpiresAt
  const justCancelled = one(sp.cancelled) === '1' && order.status === 'cancelled'
  const canCancel = order.status === 'pending_payment' || order.status === 'reserved'
  const lines = addressLines(site)

  const panel = (() => {
    switch (order.status) {
      case 'pending_payment':
        return {
          tone: 'pending',
          icon: 'clock',
          title: 'Waiting for payment',
          body: (
            <>
              {paymentNotCompleted ? <p>The payment was not completed.</p> : null}
              <p>
                Your {order.items.length === 1 ? 'piece is' : 'pieces are'} held until <strong>{formatWhen(holdEnds)}</strong>. Finish paying before
                then, or {order.items.length === 1 ? 'it' : 'they'} will return to the shop.
              </p>
            </>
          ),
          actions: order.payment?.billUrl ? (
            <a className="btn" href={order.payment.billUrl}>
              Pay now <Icon name="arrow" />
            </a>
          ) : (
            <a className="btn btn--secondary" href={wa} target="_blank" rel="noopener noreferrer">
              <Icon name="whatsapp" /> Message us about payment
            </a>
          ),
        }
      case 'reserved':
        return {
          tone: 'pending',
          icon: 'store',
          title: `Reserved until ${formatWhen(holdEnds)}`,
          body: (
            <>
              <p>Pay and collect at the boutique before then. Please message us first so we have your piece ready.</p>
              {lines.length ? (
                <address className="order-address">
                  {lines.map((l) => (
                    <span key={l}>
                      {l}
                      <br />
                    </span>
                  ))}
                </address>
              ) : null}
            </>
          ),
          actions: (
            <>
              <a className="btn" href={wa} target="_blank" rel="noopener noreferrer">
                <Icon name="whatsapp" /> Message us on WhatsApp
                <span className="visually-hidden">(opens WhatsApp)</span>
              </a>
              {site.mapsUrl ? (
                <a className="btn btn--secondary" href={site.mapsUrl} target="_blank" rel="noopener noreferrer">
                  <Icon name="map" /> Directions
                  <span className="visually-hidden">(opens Google Maps)</span>
                </a>
              ) : null}
            </>
          ),
        }
      case 'paid':
        return {
          tone: 'success',
          icon: 'check',
          title: 'Payment received, thank you',
          body: (
            <p>
              {order.fulfilment === 'delivery'
                ? 'We will pack your order and message you when it is on its way.'
                : 'We will message you when your piece is ready to collect at the boutique.'}{' '}
              A receipt is on its way to {order.contact.email}.
            </p>
          ),
          actions: null,
        }
      case 'completed':
        return {
          tone: 'success',
          icon: 'check',
          title: order.fulfilment === 'delivery' ? 'Delivered, thank you' : 'Collected, thank you',
          body: <p>This order is complete. We hope you enjoy your piece.</p>,
          actions: (
            <Link className="btn btn--secondary" href="/shop">
              Browse the shop
            </Link>
          ),
        }
      case 'cancelled':
        return {
          tone: 'neutral',
          icon: 'info',
          title: justCancelled ? 'Your order has been cancelled' : 'This order was cancelled',
          body: <p>Nothing was charged. The {order.items.length === 1 ? 'piece has' : 'pieces have'} gone back to the shop.</p>,
          actions: (
            <Link className="btn btn--secondary" href="/shop">
              Back to the shop
            </Link>
          ),
        }
      case 'expired':
        return {
          tone: 'neutral',
          icon: 'clock',
          title: 'The hold ended',
          body: <p>This order was not completed in time, so the {order.items.length === 1 ? 'piece was' : 'pieces were'} released back to the shop.</p>,
          actions: (
            <Link className="btn" href="/shop">
              Visit the shop <Icon name="arrow" />
            </Link>
          ),
        }
      case 'refund_due':
        return {
          tone: 'error',
          icon: 'alert',
          title: 'We will contact you about a refund',
          body: (
            <p>
              There was a problem with this order, so we are arranging a refund for you. We will get in touch at {order.contact.email}. You can also
              message us any time.
            </p>
          ),
          actions: (
            <a className="btn btn--secondary" href={wa} target="_blank" rel="noopener noreferrer">
              <Icon name="whatsapp" /> Message us
            </a>
          ),
        }
      default:
        return { tone: 'neutral', icon: 'info', title: 'Order', body: null, actions: null }
    }
  })()

  const addr = order.deliveryAddress

  return (
    <>
      <section className="page-hero">
        <div className="container">
          <p className="eyebrow">Order</p>
          <h1>{order.orderNumber}</h1>
          <p className="lead">Placed {formatDay(order.createdAt)}</p>
        </div>
      </section>
      <section className="section section--tight">
        <div className="container order">
          <div className="order__main stack" style={{ ['--stack-gap' as string]: 'var(--s-6)' }}>
            <div className={`status-panel status-panel--${panel.tone}`} role="status">
              <Icon name={panel.icon} size={28} />
              <div className="stack" style={{ ['--stack-gap' as string]: 'var(--s-3)' }}>
                <h2 className="status-panel__title">{panel.title}</h2>
                {panel.body}
                {panel.actions || canCancel ? (
                  <div className="cluster">
                    {panel.actions}
                    {canCancel ? (
                      <form action={cancelOrderAction}>
                        <input type="hidden" name="orderNumber" value={order.orderNumber} />
                        <input type="hidden" name="token" value={token} />
                        <button type="submit" className="btn btn--secondary">
                          Cancel this order
                        </button>
                      </form>
                    ) : null}
                  </div>
                ) : null}
              </div>
            </div>

            <section aria-labelledby="order-items">
              <h2 id="order-items" className="cart__h2">
                Items
              </h2>
              <ul className="order-items">
                {order.items.map((i, idx) => (
                  <li key={i.id ?? idx}>
                    <div>
                      {i.brand ? <p className="product-card__brand">{i.brand}</p> : null}
                      <p className="summary-item__title">{i.title}</p>
                    </div>
                    <p className="price">{formatRM(i.price)}</p>
                  </li>
                ))}
              </ul>
              <dl className="totals">
                <div>
                  <dt>Subtotal</dt>
                  <dd className="price">{formatRM(order.subtotal)}</dd>
                </div>
                {order.deliveryFee ? (
                  <div>
                    <dt>Delivery</dt>
                    <dd className="price">{formatRM(order.deliveryFee)}</dd>
                  </div>
                ) : null}
                <div className="totals__total">
                  <dt>Total</dt>
                  <dd className="price">{formatRM(order.total)}</dd>
                </div>
              </dl>
            </section>
          </div>

          <aside className="order__side card" aria-labelledby="order-info">
            <h2 id="order-info" className="cart__h2">
              Order details
            </h2>
            <dl className="spec spec--stacked">
              <div>
                <dt>Order number</dt>
                <dd>{order.orderNumber}</dd>
              </div>
              <div>
                <dt>Placed</dt>
                <dd>{formatWhen(order.createdAt)}</dd>
              </div>
              <div>
                <dt>Payment</dt>
                <dd>{METHOD_LABEL[order.paymentMethod]}</dd>
              </div>
              {order.payment?.paidAt ? (
                <div>
                  <dt>Paid</dt>
                  <dd>{formatWhen(order.payment.paidAt)}</dd>
                </div>
              ) : null}
              <div>
                <dt>{order.fulfilment === 'delivery' ? 'Delivery' : 'Collection'}</dt>
                <dd>
                  {order.fulfilment === 'delivery' && addr
                    ? [addr.line1, addr.line2, [addr.postcode, addr.city].filter(Boolean).join(' '), addr.state].filter(Boolean).join(', ')
                    : 'At the boutique in Ampang'}
                </dd>
              </div>
              <div>
                <dt>Contact</dt>
                <dd>
                  {order.contact.name}
                  <br />
                  {order.contact.email}
                  <br />
                  {order.contact.phone}
                </dd>
              </div>
              {order.customerNote ? (
                <div>
                  <dt>Your note</dt>
                  <dd>{order.customerNote}</dd>
                </div>
              ) : null}
            </dl>
            <p className="meta">
              Questions? <a href={wa} target="_blank" rel="noopener noreferrer">Message us on WhatsApp</a> and quote {order.orderNumber}.
            </p>
          </aside>
        </div>
      </section>
    </>
  )
}
