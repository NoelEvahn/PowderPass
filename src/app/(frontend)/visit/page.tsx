import type { Metadata } from 'next'
import Link from 'next/link'
import React from 'react'

import { Icon } from '@/components/Icon'
import { getServerURL } from '@/lib/env'
import { addressLines, getSite, telLink, whatsappLink } from '@/lib/site'

export async function generateMetadata(): Promise<Metadata> {
  const site = await getSite()
  const place = [site.address?.city, site.address?.state].filter(Boolean).join(', ')
  const description = `Visit ${site.businessName || 'Label Trunc'}${place ? ` in ${place}` : ''}. Address, directions and how to book a visit.`
  return {
    title: 'Visit the boutique',
    description,
    alternates: { canonical: '/visit' },
    openGraph: { title: 'Visit the boutique', description, url: '/visit' },
  }
}

export default async function VisitPage() {
  const site = await getSite()
  const lines = addressLines(site)
  const hours = (site.hours ?? []).filter((h) => h.days && h.time)
  const bookText = 'Hi Label Trunc, I would like to book a visit.'
  const base = getServerURL()

  const jsonLd = {
    '@context': 'https://schema.org',
    '@type': 'Store',
    name: site.businessName || 'Label Trunc',
    description: site.description || undefined,
    url: base,
    image: `${base}/labeltrunc-icon.png`,
    telephone: site.phone,
    email: site.email,
    address: site.address
      ? {
          '@type': 'PostalAddress',
          streetAddress: [site.address.line1, site.address.line2].filter(Boolean).join(', '),
          addressLocality: site.address.city,
          addressRegion: site.address.state,
          postalCode: site.address.postcode,
          addressCountry: site.address.country === 'Malaysia' || !site.address.country ? 'MY' : site.address.country,
        }
      : undefined,
    hasMap: site.mapsUrl || undefined,
    sameAs: (site.socials ?? []).map((s) => s.url),
    openingHours: hours.length ? hours.map((h) => `${h.days} ${h.time}`) : undefined,
  }

  return (
    <>
      <header className="page-hero">
        <div className="container">
          <nav className="breadcrumbs" aria-label="Breadcrumb">
            <ol>
              <li>
                <Link href="/">Home</Link>
              </li>
              <li aria-current="page">Visit</li>
            </ol>
          </nav>
          <h1>Visit the boutique</h1>
          {site.visitNote ? <p className="lead">{site.visitNote}</p> : null}
        </div>
      </header>

      <section className="section" aria-label="Visit details">
        <div className="container visit-grid">
          <article className="visit-card" data-reveal>
            <span className="visit-card__icon">
              <Icon name="map" size={22} />
            </span>
            <h2>Address</h2>
            <address className="visit-card__address">
              {lines.map((l) => (
                <span key={l}>{l}</span>
              ))}
            </address>
            {site.mapsUrl ? (
              <a className="btn btn--secondary" href={site.mapsUrl} target="_blank" rel="noopener noreferrer">
                Get directions <Icon name="external" />
                <span className="visually-hidden"> (opens Google Maps in a new tab)</span>
              </a>
            ) : null}
          </article>

          <article className="visit-card" data-reveal>
            <span className="visit-card__icon">
              <Icon name="clock" size={22} />
            </span>
            <h2>Opening hours</h2>
            {hours.length ? (
              <table className="hours">
                <caption className="visually-hidden">Opening hours</caption>
                <thead className="visually-hidden">
                  <tr>
                    <th scope="col">Days</th>
                    <th scope="col">Hours</th>
                  </tr>
                </thead>
                <tbody>
                  {hours.map((h, i) => (
                    <tr key={h.id ?? i}>
                      <th scope="row">{h.days}</th>
                      <td>{h.time}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            ) : (
              <p>Opening hours: please message us to arrange a time.</p>
            )}
          </article>

          <article className="visit-card visit-card--book on-dark" data-reveal>
            <span className="visit-card__icon">
              <Icon name="chat" size={22} />
            </span>
            <h2>Book a visit</h2>
            <p>Tell us when suits you and which pieces you would like to see.</p>
            <div className="stack" style={{ ['--stack-gap' as string]: 'var(--s-3)' }}>
              <a className="btn btn--light" href={whatsappLink(site, bookText)} target="_blank" rel="noopener noreferrer">
                <Icon name="whatsapp" /> Book on WhatsApp
                <span className="visually-hidden"> (opens in a new tab)</span>
              </a>
              <ul className="visit-links">
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
                <li>
                  <Link href="/contact?topic=visit">
                    <Icon name="chat" size={18} /> Send a message instead
                  </Link>
                </li>
              </ul>
            </div>
          </article>
        </div>
      </section>

      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: JSON.stringify(jsonLd).replace(/</g, '\\u003c') }}
      />
    </>
  )
}
