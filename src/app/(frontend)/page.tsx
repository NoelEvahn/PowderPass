import Link from 'next/link'
import React from 'react'

import { Icon, type IconName } from '@/components/Icon'
import { LitStatement } from '@/components/home/LitStatement'
import { MediaImage } from '@/components/MediaImage'
import { Monogram } from '@/components/site/Wordmark'
import { ProductCard } from '@/components/shop/ProductCard'
import { getShopSettings } from '@/lib/commerce/settings'
import { getPayloadClient } from '@/lib/payload'
import { getCartId } from '@/lib/session'
import { latestProducts } from '@/lib/shop'
import { addressLines, getHome, getSite, whatsappLink } from '@/lib/site'

const HIGHLIGHT_ICONS: IconName[] = ['shield', 'clock', 'card', 'truck']

export default async function HomePage() {
  const payload = await getPayloadClient()
  const [home, site, settings, cartId] = await Promise.all([getHome(), getSite(), getShopSettings(payload), getCartId()])
  const products = await latestProducts(8, cartId)
  const lines = addressLines(site)
  const instagram = site.socials?.find((s) => s.platform === 'instagram')
  const hasHours = Boolean(site.hours?.length)

  const steps = [
    {
      title: 'Choose a piece',
      text: `Add it to your cart and it is held for you for up to ${settings.cartHoldHours} hours. No one else can buy it meanwhile.`,
    },
    {
      title: settings.onlinePayment ? 'Pay online, or reserve' : 'Reserve it',
      text: settings.onlinePayment
        ? 'Pay securely with FPX online banking (Maybank2u, HSBC, Public Bank and more), cards or Touch ’n Go eWallet. Or reserve and pay at the boutique.'
        : `Reserve it online and pay when you come in. We hold it for ${settings.payAtShopHoldHours} hours.`,
    },
    {
      title: settings.delivery ? 'Collect or have it delivered' : 'Collect at the boutique',
      text: settings.delivery
        ? settings.deliveryNote || 'Collect in Ampang, or choose delivery at checkout.'
        : 'Pick it up in Ampang. Message us first and we will have it ready for you.',
    },
  ]

  return (
    <>
      <section className="home-hero">
        <div className="container home-hero__grid">
          <div className="home-hero__copy">
            <p className="eyebrow">Boutique · Ampang, Selangor</p>
            <h1 className="home-hero__title">{home.hero?.heading}</h1>
            {home.hero?.intro ? <p className="lead">{home.hero.intro}</p> : null}
            <div className="cluster home-hero__actions">
              <Link href="/shop" className="btn">
                Shop the boutique <Icon name="arrow" />
              </Link>
              <Link href="/visit" className="btn btn--secondary">
                Plan a visit
              </Link>
            </div>
          </div>
          <div className="home-hero__visual">
            {home.hero?.image && typeof home.hero.image === 'object' ? (
              <div className="home-hero__photo">
                <MediaImage media={home.hero.image} sizes="(min-width: 960px) 45vw, 92vw" priority />
              </div>
            ) : (
              <div className="tag-card">
                <span className="tag-card__hole" aria-hidden="true" />
                <div className="tag-card__head">
                  <Monogram size={52} />
                  <div>
                    <p className="tag-card__name">
                      labeltrunc<span>.</span>
                    </p>
                    <p className="tag-card__motto">Curated · Collected · Consigned</p>
                  </div>
                </div>
                <address className="tag-card__address">
                  {lines.map((l) => (
                    <span key={l}>
                      {l}
                      <br />
                    </span>
                  ))}
                </address>
                <p className="tag-card__note">{hasHours ? 'Open to visitors' : 'Visits by appointment recommended'}</p>
              </div>
            )}
          </div>
        </div>
        <div className="container">
          <a href="#new-in" className="scroll-cue">
            <span className="scroll-cue__circle">
              <Icon name="arrow-down" size={18} />
            </span>
            See what is in the boutique
          </a>
        </div>
      </section>

      {home.highlights?.length ? (
        <section className="facts" aria-label="Why buy from Label Trunc">
          <div className="container">
            <ul className="facts__list">
              {home.highlights.map((h, i) => (
                <li key={h.id ?? h.title} className="facts__item">
                  <Icon name={HIGHLIGHT_ICONS[i % HIGHLIGHT_ICONS.length]} size={24} />
                  <div>
                    <p className="facts__title">{h.title}</p>
                    {h.text ? <p className="facts__text">{h.text}</p> : null}
                  </div>
                </li>
              ))}
            </ul>
          </div>
        </section>
      ) : null}

      <section id="new-in" className="section" tabIndex={-1} aria-labelledby="new-in-heading">
        <div className="container">
          <div className="section-head">
            <div>
              <p className="eyebrow">Stock</p>
              <h2 id="new-in-heading" data-reveal>
                {home.stockHeading || 'New in the boutique'}
              </h2>
            </div>
            {products.length ? (
              <Link href="/shop" className="link-arrow">
                View all stock <Icon name="arrow" />
              </Link>
            ) : null}
          </div>
          {products.length ? (
            <div className="product-grid">
              {products.map((p) => (
                <ProductCard key={p.id} product={p} />
              ))}
            </div>
          ) : (
            <div className="empty-state">
              <Icon name="bag" size={32} />
              <h3>New pieces are being photographed</h3>
              <p>
                Our stock is going online now. Until then, see the latest arrivals on Instagram or message us to ask what
                is in the boutique this week.
              </p>
              <div className="cluster">
                {instagram ? (
                  <a className="btn" href={instagram.url} target="_blank" rel="noopener noreferrer">
                    <Icon name="instagram" /> {instagram.handle}
                  </a>
                ) : null}
                <a className="btn btn--secondary" href={whatsappLink(site, 'Hi Label Trunc, what is in the boutique this week?')} target="_blank" rel="noopener noreferrer">
                  <Icon name="whatsapp" /> Ask on WhatsApp
                </a>
              </div>
            </div>
          )}
        </div>
      </section>

      <section className="section section--tint" aria-labelledby="how-heading">
        <div className="container">
          <div className="section-head">
            <div>
              <p className="eyebrow">How buying works</p>
              <h2 id="how-heading" data-reveal>
                Hold it, pay, take it home
              </h2>
            </div>
            <Link href="/info" className="link-arrow">
              Buying information <Icon name="arrow" />
            </Link>
          </div>
          <ol className="steps">
            {steps.map((s, i) => (
              <li key={s.title} className="steps__item" data-reveal>
                <span className="steps__num" aria-hidden="true">
                  {String(i + 1).padStart(2, '0')}
                </span>
                <h3>{s.title}</h3>
                <p>{s.text}</p>
              </li>
            ))}
          </ol>
        </div>
      </section>

      <LitStatement words={['Curated.', 'Collected.', 'Consigned.']} caption="Luxury deserves to be loved again." />

      <section className="section" aria-labelledby="about-heading">
        <div className="container about-split">
          <div className="stack" style={{ ['--stack-gap' as string]: '20px' }}>
            <p className="eyebrow">About</p>
            <h2 id="about-heading" data-reveal>
              {home.about?.heading || 'What is Label Trunc?'}
            </h2>
            {home.about?.body ? <p className="lead">{home.about.body}</p> : null}
            <Link href="/about" className="link-arrow">
              More about us <Icon name="arrow" />
            </Link>
          </div>
          {home.consign?.enabled ? (
            <aside className="consign-card" aria-labelledby="consign-heading">
              <Icon name="chat" size={28} />
              <h3 id="consign-heading">{home.consign.heading}</h3>
              {home.consign.body ? <p>{home.consign.body}</p> : null}
              <Link href="/contact?topic=consign" className="btn btn--secondary">
                Ask about consigning <Icon name="arrow" />
              </Link>
            </aside>
          ) : null}
        </div>
      </section>

      <section className="section section--dark on-dark visit-band" aria-labelledby="visit-heading">
        <div className="container visit-band__grid">
          <div className="stack" style={{ ['--stack-gap' as string]: '16px' }}>
            <p className="eyebrow">Visit</p>
            <h2 id="visit-heading">Come and hold things</h2>
            <address className="visit-band__address">
              {lines.map((l) => (
                <span key={l}>
                  {l}
                  <br />
                </span>
              ))}
            </address>
          </div>
          <div className="stack" style={{ ['--stack-gap' as string]: '16px' }}>
            {hasHours ? (
              <dl className="hours">
                {site.hours!.map((h) => (
                  <div key={h.id ?? h.days}>
                    <dt>{h.days}</dt>
                    <dd>{h.time}</dd>
                  </div>
                ))}
              </dl>
            ) : null}
            {site.visitNote ? <p className="lead">{site.visitNote}</p> : null}
            <div className="cluster">
              <a className="btn btn--light" href={whatsappLink(site, 'Hi Label Trunc, I would like to book a visit.')} target="_blank" rel="noopener noreferrer">
                <Icon name="whatsapp" /> Book on WhatsApp
              </a>
              {site.mapsUrl ? (
                <a className="btn btn--ghost-light" href={site.mapsUrl} target="_blank" rel="noopener noreferrer">
                  <Icon name="map" /> Directions
                  <span className="visually-hidden">(opens Google Maps)</span>
                </a>
              ) : null}
            </div>
          </div>
        </div>
      </section>
    </>
  )
}
