import Link from 'next/link'
import React from 'react'

import { Icon } from '@/components/Icon'

export default function NotFound() {
  return (
    <section className="section">
      <div className="container status-page">
        <p className="eyebrow">Error 404</p>
        <h1>We could not find that page</h1>
        <p className="lead">The link may be old, or the piece may have found a new home. These places might help.</p>
        <div className="cluster">
          <Link href="/shop" className="btn">
            Browse the shop <Icon name="arrow" />
          </Link>
          <Link href="/" className="btn btn--secondary">
            Go to the home page
          </Link>
          <Link href="/contact" className="btn btn--secondary">
            Contact us
          </Link>
        </div>
      </div>
    </section>
  )
}
