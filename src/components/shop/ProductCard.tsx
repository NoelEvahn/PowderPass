import Link from 'next/link'
import React from 'react'

import { Icon } from '@/components/Icon'
import { MediaImage } from '@/components/MediaImage'
import { formatRM } from '@/lib/catalog'
import { brandName, coverImage, type ShopProduct } from '@/lib/shop'

export const AvailabilityBadge = ({ product }: { product: ShopProduct }) => {
  switch (product.availability) {
    case 'sold':
      return <span className="badge badge--sold">Sold</span>
    case 'held':
      return <span className="badge badge--held">{product.heldByViewer ? 'In your cart' : 'On hold'}</span>
    case 'boutique':
      return <span className="badge">In the boutique</span>
    default:
      return null
  }
}

export const ProductCard = ({ product, priority = false }: { product: ShopProduct; priority?: boolean }) => {
  const cover = coverImage(product)
  return (
    <article className={`product-card${product.availability === 'sold' ? ' product-card--sold' : ''}`}>
      <div className="product-card__media">
        {cover ? (
          <MediaImage media={cover} sizes="(min-width: 1200px) 25vw, (min-width: 640px) 45vw, 92vw" priority={priority} />
        ) : (
          <span className="product-card__noimage">
            <Icon name="bag" size={40} />
          </span>
        )}
        <AvailabilityBadge product={product} />
      </div>
      <div className="stack" style={{ ['--stack-gap' as string]: '4px' }}>
        <p className="product-card__brand">{brandName(product)}</p>
        <h3 className="product-card__title">
          <Link href={`/shop/${product.slug}`}>{product.title}</Link>
        </h3>
        <div className="product-card__meta">
          <span className="price">{formatRM(product.price)}</span>
        </div>
      </div>
    </article>
  )
}
