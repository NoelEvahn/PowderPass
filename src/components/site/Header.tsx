import Link from 'next/link'
import React from 'react'

import { Icon } from '@/components/Icon'
import { countCartItems } from '@/lib/commerce/cart'
import { getPayloadClient } from '@/lib/payload'
import { getCartId, getCustomer } from '@/lib/session'
import { announcementActive, getSite } from '@/lib/site'

import { MobileMenu, NavLinks } from './HeaderNav'
import { Wordmark } from './Wordmark'

export const Header = async () => {
  const [site, customer, cartId, payload] = await Promise.all([getSite(), getCustomer(), getCartId(), getPayloadClient()])
  const cartCount = await countCartItems(payload, cartId)
  const showAnnouncement = announcementActive(site)
  const account = customer
    ? { href: '/account', label: 'Your account' }
    : { href: '/account/sign-in', label: 'Sign in' }

  return (
    <>
      {showAnnouncement ? (
        <div className="announcement">
          <div className="container">
            {site.announcement?.link ? (
              <Link href={site.announcement.link}>
                {site.announcement.text} <Icon name="arrow" size={14} />
              </Link>
            ) : (
              <p>{site.announcement?.text}</p>
            )}
          </div>
        </div>
      ) : null}
      <header className="site-header">
        <div className="container site-header__inner">
          <Link href="/" className="site-header__brand" aria-label="labeltrunc. Home">
            <Wordmark />
          </Link>
          <nav aria-label="Main" className="site-header__nav">
            <NavLinks />
          </nav>
          <div className="site-header__actions">
            <Link href={account.href} className="header-action">
              <Icon name="user" />
              <span className="header-action__label">{account.label}</span>
            </Link>
            <Link href="/cart" className="header-action header-action--cart">
              <Icon name="bag" />
              <span className="header-action__label">Cart</span>
              <span className="cart-count" aria-hidden="true">
                {cartCount}
              </span>
              <span className="visually-hidden">
                , {cartCount} {cartCount === 1 ? 'piece' : 'pieces'}
              </span>
            </Link>
            <MobileMenu accountHref={account.href} accountLabel={account.label} whatsapp={site.whatsapp} />
          </div>
        </div>
      </header>
    </>
  )
}
