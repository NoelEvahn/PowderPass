'use client'

import Link from 'next/link'
import { usePathname } from 'next/navigation'
import React, { useEffect, useRef } from 'react'

import { Icon } from '@/components/Icon'

import { MAIN_NAV } from './nav'

const isCurrent = (pathname: string, href: string) => pathname === href || pathname.startsWith(`${href}/`)

export const NavLinks = () => {
  const pathname = usePathname() ?? '/'
  return (
    <ul className="nav-links">
      {MAIN_NAV.map((item) => (
        <li key={item.href}>
          <Link href={item.href} aria-current={isCurrent(pathname, item.href) ? 'page' : undefined}>
            {item.label}
          </Link>
        </li>
      ))}
    </ul>
  )
}

/** Mobile menu in a native modal dialog: Escape closes it and focus returns to the button. */
export const MobileMenu = ({ accountHref, accountLabel, whatsapp }: { accountHref: string; accountLabel: string; whatsapp: string }) => {
  const dialogRef = useRef<HTMLDialogElement>(null)
  const pathname = usePathname()

  useEffect(() => {
    dialogRef.current?.close()
  }, [pathname])

  return (
    <>
      <button
        type="button"
        className="header-action menu-button"
        aria-haspopup="dialog"
        onClick={() => dialogRef.current?.showModal()}
      >
        <Icon name="menu" />
        <span className="header-action__label">Menu</span>
      </button>
      <dialog ref={dialogRef} className="mobile-menu" aria-label="Menu">
        <div className="mobile-menu__top">
          <span className="eyebrow">Menu</span>
          <button type="button" className="icon-button" onClick={() => dialogRef.current?.close()} aria-label="Close menu">
            <Icon name="close" size={24} />
          </button>
        </div>
        <nav aria-label="Mobile">
          <ul className="mobile-menu__links">
            <li>
              <Link href="/">Home</Link>
            </li>
            {MAIN_NAV.map((item) => (
              <li key={item.href}>
                <Link href={item.href} aria-current={pathname && isCurrent(pathname, item.href) ? 'page' : undefined}>
                  {item.label}
                </Link>
              </li>
            ))}
            <li>
              <Link href={accountHref}>{accountLabel}</Link>
            </li>
            <li>
              <Link href="/cart">Cart</Link>
            </li>
          </ul>
        </nav>
        <a className="btn btn--block" href={`https://wa.me/${whatsapp}`} target="_blank" rel="noopener noreferrer">
          <Icon name="whatsapp" /> Message us on WhatsApp
        </a>
      </dialog>
    </>
  )
}
