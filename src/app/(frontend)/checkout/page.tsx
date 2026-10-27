import type { Metadata } from 'next'
import Link from 'next/link'
import React from 'react'

import { Icon } from '@/components/Icon'
import { CheckoutForm, type CheckoutItem } from '@/components/shop/CheckoutForm'
import { getCartItems, releaseExpired } from '@/lib/commerce/cart'
import { getShopSettings } from '@/lib/commerce/settings'
import { getPayloadClient } from '@/lib/payload'
import { getCartId, getCustomer } from '@/lib/session'
import { brandName, coverImage } from '@/lib/shop'
import { getSite, whatsappLink } from '@/lib/site'
import type { Media } from '@/payload-types'

export const metadata: Metadata = {
  title: 'Checkout',
  robots: { index: false, follow: false },
}

export default async function CheckoutPage() {
  const payload = await getPayloadClient()
  await releaseExpired(payload)
  const [items, settings, customer, site] = await Promise.all([
    getCartItems(payload, await getCartId()),
    getShopSettings(payload),
    getCustomer(),
    getSite(),
  ])

  if (items.length === 0) {
    return (
      <>
        <section className="page-hero">
          <div className="container">
            <h1>Checkout</h1>
          </div>
        </section>
        <section className="section section--tight">
          <div className="container">
            <div className="empty-state">
              <Icon name="bag" size={32} />
              <h2>Your cart is empty</h2>
              <p>There is nothing to check out. If you had a piece in your cart, its hold may have ended and it has returned to the shop.</p>
              <Link className="btn" href="/shop">
                Browse the shop <Icon name="arrow" />
              </Link>
            </div>
          </div>
        </section>
      </>
    )
  }

  const checkoutItems: CheckoutItem[] = items.map((i) => {
    const cover = coverImage(i.product)
    return {
      id: i.product.id,
      slug: i.product.slug ?? '',
      title: i.product.title,
      brand: brandName(i.product),
      price: i.product.price,
      image: (cover as Media | null) ?? null,
    }
  })
  const earliest = items.map((i) => i.expiresAt).sort()[0]

  return (
    <>
      <section className="page-hero">
        <div className="container">
          <nav className="breadcrumbs" aria-label="Breadcrumb">
            <ol>
              <li>
                <Link href="/cart">Cart</Link>
              </li>
              <li aria-current="page">Checkout</li>
            </ol>
          </nav>
          <h1>Checkout</h1>
        </div>
      </section>
      <section className="section section--tight">
        <div className="container">
          <CheckoutForm
            items={checkoutItems}
            earliestHold={earliest}
            signedIn={Boolean(customer)}
            defaults={{ name: customer?.name ?? '', email: customer?.email ?? '', phone: customer?.phone ?? '' }}
            pickupPlace={[site.address?.line1, site.address?.city].filter(Boolean).join(', ') || 'the boutique'}
            whatsappHref={whatsappLink(site, 'Hi Label Trunc, I need help with my order.')}
            settings={{
              onlinePayment: settings.onlinePayment,
              payAtShop: settings.payAtShop,
              delivery: settings.delivery,
              deliveryFee: settings.deliveryFee,
              deliveryNote: settings.deliveryNote,
              payAtShopHoldHours: settings.payAtShopHoldHours,
            }}
          />
        </div>
      </section>
    </>
  )
}
