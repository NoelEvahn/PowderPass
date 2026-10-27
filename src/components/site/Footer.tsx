import Link from 'next/link'
import React from 'react'

import { Icon } from '@/components/Icon'
import { addressLines, getSite, telLink, whatsappLink } from '@/lib/site'

import { MAIN_NAV } from './nav'
import { Wordmark } from './Wordmark'

const POLICY_LINKS = [
  { href: '/privacy', label: 'Privacy' },
  { href: '/privasi', label: 'Privasi (BM)' },
  { href: '/cookies', label: 'Cookies' },
  { href: '/terms', label: 'Terms' },
  { href: '/refunds', label: 'Refunds & returns' },
  { href: '/accessibility', label: 'Accessibility' },
  { href: '/business-details', label: 'Business details' },
]

export const Footer = async () => {
  const site = await getSite()
  const lines = addressLines(site)
  return (
    <footer className="site-footer on-dark">
      <div className="container">
        <div className="site-footer__grid">
          <div className="stack">
            <Link href="/" className="site-footer__brand" aria-label="labeltrunc. Home">
              <Wordmark size={40} />
            </Link>
            {site.tagline ? <p className="site-footer__tagline">{site.tagline}</p> : null}
          </div>
          <div className="stack">
            <h2 className="site-footer__heading">Visit</h2>
            <address>
              {lines.map((l) => (
                <span key={l}>
                  {l}
                  <br />
                </span>
              ))}
            </address>
            {site.mapsUrl ? (
              <a href={site.mapsUrl} target="_blank" rel="noopener noreferrer" className="link-arrow">
                Directions <Icon name="external" size={16} />
                <span className="visually-hidden">(opens Google Maps)</span>
              </a>
            ) : null}
          </div>
          <div className="stack">
            <h2 className="site-footer__heading">Contact</h2>
            <ul className="footer-list">
              <li>
                <a href={whatsappLink(site)} target="_blank" rel="noopener noreferrer">
                  <Icon name="whatsapp" size={18} /> WhatsApp {site.phone}
                </a>
              </li>
              <li>
                <a href={telLink(site.phone)}>
                  <Icon name="phone" size={18} /> Call {site.phone}
                </a>
              </li>
              <li>
                <a href={`mailto:${site.email}`}>
                  <Icon name="mail" size={18} /> {site.email}
                </a>
              </li>
              {(site.socials ?? []).map((s) => (
                <li key={s.url}>
                  <a href={s.url} target="_blank" rel="noopener noreferrer">
                    <Icon name={s.platform} size={18} /> {s.handle}
                    <span className="visually-hidden"> on {s.platform}</span>
                  </a>
                </li>
              ))}
            </ul>
          </div>
          <nav className="stack" aria-label="Footer">
            <h2 className="site-footer__heading">Explore</h2>
            <ul className="footer-list">
              {MAIN_NAV.map((l) => (
                <li key={l.href}>
                  <Link href={l.href}>{l.label}</Link>
                </li>
              ))}
              <li>
                <Link href="/account">Your account</Link>
              </li>
            </ul>
          </nav>
        </div>
        <div className="site-footer__legal">
          <ul className="footer-inline">
            {POLICY_LINKS.map((l) => (
              <li key={l.href}>
                <Link href={l.href}>{l.label}</Link>
              </li>
            ))}
          </ul>
          <p>
            Label Trunc is an independent boutique and is not affiliated with, endorsed by or sponsored by any designer
            house. Brand names belong to their owners.
          </p>
          <p>
            &copy; {new Date().getFullYear()} {site.businessName}
          </p>
        </div>
      </div>
    </footer>
  )
}
