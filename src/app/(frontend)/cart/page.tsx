import type { Metadata } from 'next'
import Link from 'next/link'
import React from 'react'

import { removeFromCartAction } from '@/app/(frontend)/actions/cart'
import { Icon } from '@/components/Icon'
import { MediaImage } from '@/components/MediaImage'
import { HoldCountdown } from '@/components/shop/HoldCountdown'
import { formatRM } from '@/lib/catalog'
import { getCartItems, releaseExpired } from '@/lib/commerce/cart'
import { getShopSettings } from '@/lib/commerce/settings'
import { getPayloadClient } from '@/lib/payload'
import { getCartId } from '@/lib/session'
import { brandName, coverImage } from '@/lib/shop'

export const metadata: Metadata = {
  title: 'Your cart',
  robots: { index: false, follow: false },
}

export default async function CartPage() {
  const payload = await getPayloadClient()
  await releaseExpired(payload)
  const [items, settings] = await Promise.all([getCartItems(payload, await getCartId()), getShopSettings(payload)])
  const subtotal = items.reduce((sum, i) => sum + i.product.price, 0)

  return (
    <>
      <section className="page-hero">
        <div className="container">
          <h1>Your cart</h1>
        </div>
      </section>
      <section className="section section--tight">
        <div className="container">
          {items.length === 0 ? (
            <div className="empty-state">
              <Icon name="bag" size={32} />
              <h2>Your cart is empty</h2>
              <p>
                Pieces you add are held for you for up to {settings.cartHoldHours} hours. If a hold ran out, the piece went back to the shop.
              </p>
              <Link className="btn" href="/shop">
                Browse the shop <Icon name="arrow" />
              </Link>
            </div>
          ) : (
            <div className="cart">
              <div className="cart__list">
                <p className="muted small">
                  {items.length} of {settings.maxItemsPerCart} pieces. We hold each piece for you for up to {settings.cartHoldHours}{' '}
                  {settings.cartHoldHours === 1 ? 'hour' : 'hours'}. After that it returns to the shop.
                </p>
                <ul className="cart-items">
                  {items.map((item) => {
                    const cover = coverImage(item.product)
                    return (
                      <li key={item.reservationId} className="cart-item">
                        <Link href={`/shop/${item.product.slug}`} className="cart-item__media" tabIndex={-1} aria-hidden="true">
                          {cover ? (
                            <MediaImage media={cover} alt="" sizes="120px" />
                          ) : (
                            <span className="product-card__noimage">
                              <Icon name="bag" size={28} />
                            </span>
                          )}
                        </Link>
                        <div className="cart-item__body">
                          <p className="product-card__brand">{brandName(item.product)}</p>
                          <h2 className="cart-item__title">
                            <Link href={`/shop/${item.product.slug}`}>{item.product.title}</Link>
                          </h2>
                          <p className="cart-item__hold small">
                            <HoldCountdown expiresAt={item.expiresAt} />
                          </p>
                        </div>
                        <div className="cart-item__side">
                          <p className="price">{formatRM(item.product.price)}</p>
                          <form action={removeFromCartAction}>
                            <input type="hidden" name="productId" value={item.product.id} />
                            <button type="submit" className="btn btn--secondary btn--small">
                              <Icon name="trash" /> Remove
                              <span className="visually-hidden"> {item.product.title}</span>
                            </button>
                          </form>
                        </div>
                      </li>
                    )
                  })}
                </ul>
              </div>
              <aside className="cart__summary card" aria-labelledby="cart-summary">
                <h2 id="cart-summary" className="cart__h2">
                  Summary
                </h2>
                <dl className="totals">
                  <div>
                    <dt>Subtotal</dt>
                    <dd className="price">{formatRM(subtotal)}</dd>
                  </div>
                </dl>
                <p className="meta">{settings.delivery ? 'Choose collection or delivery at checkout.' : 'Collect at the boutique in Ampang.'}</p>
                <Link href="/checkout" className="btn btn--block">
                  Checkout <Icon name="arrow" />
                </Link>
                <Link href="/shop" className="link-arrow">
                  Keep browsing
                </Link>
              </aside>
            </div>
          )}
        </div>
      </section>
    </>
  )
}
