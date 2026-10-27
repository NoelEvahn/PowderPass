import Link from 'next/link'
import React from 'react'

/** Shortcuts at the top of the editor dashboard for the most common jobs. */
export const Welcome = () => (
  <div
    style={{
      marginBottom: 32,
      padding: '20px 24px',
      border: '1px solid var(--theme-elevation-150)',
      borderRadius: 12,
      background: 'var(--theme-elevation-50)',
    }}
  >
    <h2 style={{ margin: '0 0 8px', fontSize: 20 }}>Welcome to the Label Trunc editor</h2>
    <p style={{ margin: '0 0 16px', maxWidth: 680 }}>
      Add a new piece with photos, check today&rsquo;s orders, or update your contact details. Changes to stock and
      pages are saved as drafts until you press <strong>Publish</strong>.
    </p>
    <div style={{ display: 'flex', flexWrap: 'wrap', gap: 12 }}>
      <Link className="btn btn--style-primary btn--size-medium" href="/admin/collections/products/create" style={{ margin: 0 }}>
        Add a piece
      </Link>
      <Link className="btn btn--style-secondary btn--size-medium" href="/admin/collections/orders" style={{ margin: 0 }}>
        Orders
      </Link>
      <Link className="btn btn--style-secondary btn--size-medium" href="/admin/collections/enquiries" style={{ margin: 0 }}>
        Enquiries
      </Link>
      <a className="btn btn--style-secondary btn--size-medium" href="/" target="_blank" rel="noreferrer" style={{ margin: 0 }}>
        View the website
      </a>
    </div>
  </div>
)
