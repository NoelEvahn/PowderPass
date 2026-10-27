'use client'

import Link from 'next/link'
import React, { useEffect } from 'react'

export default function ErrorPage({ error, reset }: { error: Error & { digest?: string }; reset: () => void }) {
  useEffect(() => {
    console.error(error)
  }, [error])

  return (
    <section className="section">
      <div className="container status-page">
        <p className="eyebrow">Something went wrong</p>
        <h1>This page did not load</h1>
        <p className="lead">It is not your doing. Please try again, or message us and we will help.</p>
        <div className="cluster">
          <button type="button" className="btn" onClick={() => reset()}>
            Try again
          </button>
          <Link href="/" className="btn btn--secondary">
            Go to the home page
          </Link>
          <Link href="/contact" className="btn btn--secondary">
            Contact us
          </Link>
        </div>
        {error.digest ? <p className="meta">Reference: {error.digest}</p> : null}
      </div>
    </section>
  )
}
