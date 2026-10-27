import type { Metadata } from 'next'
import { draftMode } from 'next/headers'
import Link from 'next/link'
import { notFound } from 'next/navigation'
import React, { cache } from 'react'

import { Icon } from '@/components/Icon'
import { RichText } from '@/components/RichText'
import { AddToCartForm } from '@/components/shop/AddToCartForm'
import { Gallery } from '@/components/shop/Gallery'
import { HoldCountdown } from '@/components/shop/HoldCountdown'
import { AvailabilityBadge } from '@/components/shop/ProductCard'
import { CONDITION_OPTIONS, INCLUDES_OPTIONS, formatRM, labelFor } from '@/lib/catalog'
import { getShopSettings } from '@/lib/commerce/settings'
import { getServerURL } from '@/lib/env'
import { getPayloadClient } from '@/lib/payload'
import { getCartId } from '@/lib/session'
import { brandName, getProductBySlug } from '@/lib/shop'
import { getSite, whatsappLink } from '@/lib/site'
import { formatWhen } from '@/lib/when'
import type { Media } from '@/payload-types'

type Params = { slug: string }

const load = cache(async (slug: string, draft: boolean, cartId: string | null) =>
  getProductBySlug(slug, { draft, cartId }),
)

const absolute = (url: string | null | undefined) => {
  if (!url) return undefined
  return /^https?:\/\//.test(url) ? url : `${getServerURL()}${url.startsWith('/') ? '' : '/'}${url}`
}

const titleWithBrand = (brand: string, title: string) =>
  brand && !title.toLowerCase().startsWith(brand.toLowerCase()) ? `${brand} ${title}` : title

export async function generateMetadata({ params }: { params: Promise<Params> }): Promise<Metadata> {
  const { slug } = await params
  const { isEnabled: draft } = await draftMode()
  const product = await load(slug, draft, await getCartId())
  if (!product) return { title: 'Piece not found', robots: { index: false } }
  const brand = brandName(product)
  const title = product.seo?.title || titleWithBrand(brand, product.title)
  const gallery = (product.gallery ?? []).filter((m): m is Media => typeof m === 'object')
  const cover = gallery[0]
  const og = absolute(cover?.sizes?.large?.url || cover?.sizes?.card?.url || cover?.url)
  const description =
    product.seo?.description ||
    `${titleWithBrand(brand, product.title)}, ${formatRM(product.price)}. ${
      product.condition === 'new' || product.condition === 'unused' ? 'Unused' : 'Pre-loved'
    } designer piece at Label Trunc, Ampang.`
  return {
    title,
    description,
    alternates: { canonical: `/shop/${product.slug}` },
    robots: draft ? { index: false, follow: false } : undefined,
    openGraph: { title, description, type: 'website', url: `/shop/${product.slug}`, images: og ? [{ url: og, alt: cover?.alt ?? title }] : undefined },
    twitter: { card: 'summary_large_image' },
  }
}

export default async function ProductPage({ params }: { params: Promise<Params> }) {
  const { slug } = await params
  const { isEnabled: draft } = await draftMode()
  const cartId = await getCartId()
  const product = await load(slug, draft, cartId)
  if (!product) notFound()

  const payload = await getPayloadClient()
  const [settings, site] = await Promise.all([getShopSettings(payload), getSite()])
  const brand = typeof product.brand === 'object' ? product.brand : null
  const brandLabel = brand?.name ?? ''
  const images = (product.gallery ?? []).filter((m): m is Media => typeof m === 'object')
  const url = `${getServerURL()}/shop/${product.slug}`
  const displayTitle = product.title

  const details: { label: string; value: string }[] = [
    { label: 'Condition', value: labelFor(CONDITION_OPTIONS, product.condition) },
    { label: 'Condition notes', value: product.conditionNotes ?? '' },
    {
      label: 'Comes with',
      value: (product.includes ?? []).map((v) => labelFor(INCLUDES_OPTIONS, v)).filter(Boolean).join(', '),
    },
    { label: 'Colour', value: product.colour ?? '' },
    { label: 'Material', value: product.material ?? '' },
    { label: 'Hardware', value: product.hardware ?? '' },
    { label: 'Dimensions', value: product.dimensions ?? '' },
    { label: 'Year / series', value: product.year ?? '' },
  ].filter((d) => d.value.trim())

  const availability = {
    available: 'https://schema.org/InStock',
    held: 'https://schema.org/LimitedAvailability',
    sold: 'https://schema.org/SoldOut',
    boutique: 'https://schema.org/InStoreOnly',
  }[product.availability]
  const jsonLd = {
    '@context': 'https://schema.org',
    '@type': 'Product',
    name: displayTitle,
    brand: brandLabel ? { '@type': 'Brand', name: brandLabel } : undefined,
    image: images.map((m) => absolute(m.sizes?.xlarge?.url || m.sizes?.large?.url || m.url)).filter(Boolean),
    description: product.seo?.description || undefined,
    sku: product.sku || undefined,
    color: product.colour || undefined,
    material: product.material || undefined,
    itemCondition:
      product.condition === 'new' || product.condition === 'unused'
        ? 'https://schema.org/NewCondition'
        : 'https://schema.org/UsedCondition',
    url,
    offers: {
      '@type': 'Offer',
      url,
      price: product.price,
      priceCurrency: 'MYR',
      availability,
      itemCondition:
        product.condition === 'new' || product.condition === 'unused'
          ? 'https://schema.org/NewCondition'
          : 'https://schema.org/UsedCondition',
    },
  }

  const waText = `Hi Label Trunc, I'm interested in ${titleWithBrand(brandLabel, displayTitle)} (${url})`
  const canBuy = product.availability === 'available'
  const heldByYou = product.availability === 'held' && product.heldByViewer
  const heldByOther = product.availability === 'held' && !product.heldByViewer

  const payLine = settings.onlinePayment
    ? settings.payAtShop
      ? 'Pay online (FPX, cards, e-wallets) or at the boutique'
      : 'Pay online with FPX, cards or e-wallets'
    : settings.payAtShop
      ? 'Reserve online, pay at the boutique'
      : 'Message us to arrange payment'

  return (
    <article className={`pdp${canBuy || heldByYou ? ' pdp--has-bar' : ''}`}>
      <script type="application/ld+json" dangerouslySetInnerHTML={{ __html: JSON.stringify(jsonLd).replace(/</g, '\\u003c') }} />
      <div className="container">
        <nav className="breadcrumbs pdp__crumbs" aria-label="Breadcrumb">
          <ol>
            <li>
              <Link href="/">Home</Link>
            </li>
            <li>
              <Link href="/shop">Shop</Link>
            </li>
            {brand ? (
              <li>
                <Link href={`/shop?brand=${encodeURIComponent(brand.slug ?? '')}`}>{brand.name}</Link>
              </li>
            ) : null}
          </ol>
        </nav>

        <div className="pdp__grid">
          <div className="pdp__gallery">
            <Gallery images={images} title={titleWithBrand(brandLabel, displayTitle)} />
          </div>

          <div className="pdp__details">
            <div className="pdp__panel stack" style={{ ['--stack-gap' as string]: 'var(--s-4)' }}>
              <div className="stack" style={{ ['--stack-gap' as string]: 'var(--s-2)' }}>
                {brandLabel ? <p className="eyebrow">{brandLabel}</p> : null}
                <h1 className="pdp__title">{displayTitle}</h1>
              </div>
              <div className="pdp__priceRow">
                <p className="price pdp__price">{formatRM(product.price)}</p>
                <AvailabilityBadge product={product} />
              </div>

              {product.availability === 'available' ? (
                <p className="pdp__status">One of a kind. Add it to your cart and we will hold it for you for up to {settings.cartHoldHours} hours.</p>
              ) : null}
              {heldByYou && product.heldUntil ? (
                <p className="pdp__status">
                  <HoldCountdown expiresAt={product.heldUntil} />
                </p>
              ) : null}
              {heldByOther && product.heldUntil ? (
                <p className="pdp__status">
                  On hold until <time dateTime={product.heldUntil}>{formatWhen(product.heldUntil)}</time>. It may come back if the other buyer does not complete the order.
                </p>
              ) : null}
              {product.availability === 'sold' ? (
                <p className="pdp__status">This piece has found a new home. Browse the shop for similar pieces, or ask us to look out for one.</p>
              ) : null}
              {product.availability === 'boutique' ? (
                <p className="pdp__status">In the boutique: ask us. This piece is not sold online, but we can show it to you or arrange it.</p>
              ) : null}

              <div className="pdp__actions">
                {canBuy || heldByYou ? <AddToCartForm productId={product.id} priceLabel={formatRM(product.price)} held={heldByYou} /> : null}
                {heldByYou ? (
                  <Link href="/checkout" className="btn btn--secondary btn--block">
                    Go to checkout
                  </Link>
                ) : null}
                {product.availability === 'boutique' ? (
                  <a
                    className="btn btn--block"
                    href={whatsappLink(site, waText)}
                    target="_blank"
                    rel="noopener noreferrer"
                  >
                    <Icon name="whatsapp" /> Ask on WhatsApp
                    <span className="visually-hidden">(opens WhatsApp)</span>
                  </a>
                ) : null}
                {product.availability === 'sold' ? (
                  <Link className="btn btn--block" href="/shop">
                    Browse the shop
                  </Link>
                ) : null}
                <div className="pdp__ask">
                  <Link className="btn btn--secondary btn--small" href={`/contact?topic=piece&product=${product.id}`}>
                    <Icon name="chat" /> Ask about this piece
                  </Link>
                  {product.availability !== 'boutique' ? (
                    <a className="btn btn--secondary btn--small" href={whatsappLink(site, waText)} target="_blank" rel="noopener noreferrer">
                      <Icon name="whatsapp" /> WhatsApp
                      <span className="visually-hidden">(opens WhatsApp)</span>
                    </a>
                  ) : null}
                </div>
              </div>

              <ul className="reassure" aria-label="What to expect">
                <li>
                  <Icon name="shield" />
                  <span>100% authentic, money-back guaranteed</span>
                </li>
                {product.availability === 'available' || product.availability === 'held' ? (
                  <>
                    <li>
                      <Icon name="clock" />
                      <span>Held for you for up to {settings.cartHoldHours} hours once it is in your cart</span>
                    </li>
                    <li>
                      <Icon name="card" />
                      <span>{payLine}</span>
                    </li>
                  </>
                ) : null}
                {product.availability !== 'sold' ? (
                  <li>
                    <Icon name="store" />
                    <span>
                      {settings.delivery && product.availability !== 'boutique'
                        ? 'Collect at the boutique in Ampang, or choose delivery'
                        : 'Collect at the boutique in Ampang'}
                    </span>
                  </li>
                ) : null}
              </ul>
            </div>
          </div>
        </div>

        <div className="pdp__more">
          {details.length ? (
            <section aria-labelledby="pdp-details">
              <h2 id="pdp-details" className="pdp__h2">
                Details
              </h2>
              <dl className="spec">
                {details.map((d) => (
                  <div key={d.label}>
                    <dt>{d.label}</dt>
                    <dd>{d.value}</dd>
                  </div>
                ))}
              </dl>
            </section>
          ) : null}
          {product.description ? (
            <section aria-labelledby="pdp-about">
              <h2 id="pdp-about" className="pdp__h2">
                About this piece
              </h2>
              <RichText data={product.description} />
            </section>
          ) : null}
        </div>
      </div>
    </article>
  )
}
