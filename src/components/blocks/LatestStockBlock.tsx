import Link from 'next/link'
import React from 'react'

import { Icon } from '@/components/Icon'
import { ProductCard } from '@/components/shop/ProductCard'
import { getSite } from '@/lib/site'
import { getCartId } from '@/lib/session'
import { latestProducts } from '@/lib/shop'

import type { BlockOf } from './types'

export const LatestStockBlock = async ({ block, id, tone }: { block: BlockOf<'latestStock'>; id: string; tone: string }) => {
  const limit = Math.min(12, Math.max(2, block.limit ?? 4))
  const products = await latestProducts(limit, await getCartId()).catch(() => [])
  const heading = block.heading || 'New in'

  if (!products.length) {
    const site = await getSite()
    const instagram = site.socials?.find((s) => s.platform === 'instagram')
    return (
      <section className={`section ${tone}`} aria-labelledby={id}>
        <div className="container">
          <div className="empty-state" data-reveal>
            <h2 id={id}>{heading}</h2>
            <p>
              New pieces arrive often and the shop page is being filled. Follow us for the latest arrivals
              {instagram ? ` on Instagram (${instagram.handle})` : ''}, or ask us what has just come in.
            </p>
            <div className="cluster">
              {instagram ? (
                <a className="btn" href={instagram.url} target="_blank" rel="noopener noreferrer">
                  <Icon name="instagram" /> Follow {instagram.handle}
                  <span className="visually-hidden"> (opens in a new tab)</span>
                </a>
              ) : null}
              <Link className="btn btn--secondary" href="/contact">
                Ask us
              </Link>
            </div>
          </div>
        </div>
      </section>
    )
  }

  return (
    <section className={`section ${tone}`} aria-labelledby={id}>
      <div className="container">
        <div className="section-head">
          <h2 id={id}>{heading}</h2>
          <Link href="/shop" className="link-arrow">
            View the whole shop <Icon name="arrow" />
          </Link>
        </div>
        <div className="product-grid" data-reveal>
          {products.map((p) => (
            <ProductCard key={p.id} product={p} />
          ))}
        </div>
      </div>
    </section>
  )
}
