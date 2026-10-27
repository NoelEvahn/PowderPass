import type { Metadata } from 'next'
import Link from 'next/link'
import React from 'react'

import { Icon } from '@/components/Icon'
import { ContactForm } from '@/components/forms/ContactForm'
import { nowMs } from '@/lib/time'
import { ENQUIRY_TOPICS } from '@/collections/Enquiries'
import { getPayloadClient } from '@/lib/payload'
import { getSite, telLink, whatsappLink } from '@/lib/site'

export const metadata: Metadata = {
  title: 'Contact',
  description: 'Message Label Trunc on WhatsApp, call, email or send an enquiry about a piece, a visit or consigning a bag.',
  alternates: { canonical: '/contact' },
}

type Props = { searchParams: Promise<Record<string, string | string[] | undefined>> }

const first = (v: string | string[] | undefined) => (Array.isArray(v) ? v[0] : v)

export default async function ContactPage({ searchParams }: Props) {
  const sp = await searchParams
  const site = await getSite()

  const topicParam = first(sp.topic)
  let topic = ENQUIRY_TOPICS.some((t) => t.value === topicParam) ? topicParam : undefined

  let productId: number | undefined
  let productTitle: string | undefined
  const productParam = first(sp.product)
  if (productParam && /^\d{1,9}$/.test(productParam)) {
    const payload = await getPayloadClient()
    const { docs } = await payload.find({
      collection: 'products',
      where: { and: [{ id: { equals: Number(productParam) } }, { _status: { equals: 'published' } }] },
      limit: 1,
      depth: 0,
      select: { title: true },
      draft: false,
    })
    if (docs[0]) {
      productId = docs[0].id
      productTitle = docs[0].title
      topic = topic ?? 'piece'
    }
  }

  const instagram = site.socials?.find((s) => s.platform === 'instagram')
  const options = [
    {
      key: 'whatsapp',
      icon: 'whatsapp',
      label: 'WhatsApp',
      value: site.phone,
      href: whatsappLink(site, productTitle ? `Hi Label Trunc, I would like to ask about ${productTitle}.` : 'Hi Label Trunc,'),
      external: true,
    },
    { key: 'call', icon: 'phone', label: 'Call', value: site.phone, href: telLink(site.phone), external: false },
    { key: 'email', icon: 'mail', label: 'Email', value: site.email, href: `mailto:${site.email}`, external: false },
    ...(instagram
      ? [{ key: 'instagram', icon: 'instagram', label: 'Instagram', value: instagram.handle, href: instagram.url, external: true }]
      : []),
  ]

  return (
    <>
      <header className="page-hero">
        <div className="container">
          <nav className="breadcrumbs" aria-label="Breadcrumb">
            <ol>
              <li>
                <Link href="/">Home</Link>
              </li>
              <li aria-current="page">Contact</li>
            </ol>
          </nav>
          <h1>Contact us</h1>
          <p className="lead">Ask about a piece, book a visit, or tell us about a bag you would like to consign.</p>
        </div>
      </header>

      <section className="section section--tight" aria-labelledby="quick-contact">
        <div className="container stack" style={{ ['--stack-gap' as string]: 'var(--s-5)' }}>
          <h2 id="quick-contact" className="h-small">
            Message us directly
          </h2>
          <ul className="contact-options" data-reveal>
            {options.map((o) => (
              <li key={o.key}>
                <a
                  className="contact-option"
                  href={o.href}
                  {...(o.external ? { target: '_blank', rel: 'noopener noreferrer' } : {})}
                >
                  <span className="contact-option__icon">
                    <Icon name={o.icon} size={22} />
                  </span>
                  <span className="contact-option__text">
                    <span className="contact-option__label">
                      {o.label}
                      {o.external ? <span className="visually-hidden"> (opens in a new tab)</span> : null}
                    </span>
                    <span className="contact-option__value">{o.value}</span>
                  </span>
                </a>
              </li>
            ))}
          </ul>
        </div>
      </section>

      <section className="section section--tint" aria-labelledby="enquiry-heading">
        <div className="container contact-layout">
          <div className="stack contact-layout__intro" style={{ ['--stack-gap' as string]: 'var(--s-3)' }}>
            <h2 id="enquiry-heading">Send an enquiry</h2>
            <p className="muted">
              Write to us here and we will reply by email or phone. For a quick answer, WhatsApp is usually the easiest.
            </p>
          </div>
          <div className="contact-layout__form" data-reveal>
            <ContactForm topics={ENQUIRY_TOPICS} defaultTopic={topic} productId={productId} productTitle={productTitle} startedAt={nowMs()} />
          </div>
        </div>
      </section>
    </>
  )
}
