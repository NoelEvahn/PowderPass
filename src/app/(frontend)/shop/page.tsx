import type { Metadata } from 'next'
import Link from 'next/link'
import React from 'react'

import { Icon } from '@/components/Icon'
import { ProductCard } from '@/components/shop/ProductCard'
import { CONDITION_OPTIONS, labelFor } from '@/lib/catalog'
import { getShopSettings } from '@/lib/commerce/settings'
import { getPayloadClient } from '@/lib/payload'
import { getCartId } from '@/lib/session'
import { getSite } from '@/lib/site'
import { instagramOf } from '@/lib/shop-links'
import { getFilterOptions, listProducts, type ShopFilters } from '@/lib/shop'

export const metadata: Metadata = {
  title: 'Shop',
  description: 'New and pre-loved designer bags at Label Trunc, Ampang. Every piece is unique and authenticated.',
  alternates: { canonical: '/shop' },
}

type SP = Record<string, string | string[] | undefined>

const one = (v: string | string[] | undefined) => (Array.isArray(v) ? v[0] : v)

const SORTS = [
  { value: 'newest', label: 'Newest first' },
  { value: 'price-asc', label: 'Price: low to high' },
  { value: 'price-desc', label: 'Price: high to low' },
] as const

const parse = (sp: SP) => {
  const sortRaw = one(sp.sort)
  const sold = ([] as (string | undefined)[]).concat(sp.sold)
  return {
    q: (one(sp.q) ?? '').trim().slice(0, 80),
    brand: one(sp.brand) ?? '',
    category: one(sp.category) ?? '',
    condition: one(sp.condition) ?? '',
    sort: (SORTS.find((s) => s.value === sortRaw)?.value ?? 'newest') as ShopFilters['sort'],
    // The checkbox sends sold=1 when ticked; a hidden sold=0 always precedes it.
    includeSold: sold.includes('1') || !sold.includes('0'),
    page: Math.max(1, Math.floor(Number(one(sp.page))) || 1),
  }
}

export default async function ShopPage({ searchParams }: { searchParams: Promise<SP> }) {
  const f = parse(await searchParams)
  const cartId = await getCartId()
  const payload = await getPayloadClient()
  const [settings, options, site, result] = await Promise.all([
    getShopSettings(payload),
    getFilterOptions(),
    getSite(),
    listProducts(
      { q: f.q, brand: f.brand, category: f.category, condition: f.condition, sort: f.sort, includeSold: f.includeSold, page: f.page },
      cartId,
    ),
  ])
  const instagram = instagramOf(site)
  const showSoldToggle = settings.showSoldDays > 0

  const brandLabel = options.brands.find((b) => b.slug === f.brand)?.name ?? ''
  const categoryLabel = options.categories.find((c) => c.slug === f.category)?.name ?? ''
  const active = [
    f.q ? { key: 'q', label: `“${f.q}”` } : null,
    f.brand ? { key: 'brand', label: brandLabel || f.brand } : null,
    f.category ? { key: 'category', label: categoryLabel || f.category } : null,
    f.condition ? { key: 'condition', label: labelFor(CONDITION_OPTIONS, f.condition) || f.condition } : null,
    !f.includeSold && showSoldToggle ? { key: 'sold', label: 'Sold pieces hidden' } : null,
  ].filter((x): x is { key: string; label: string } => Boolean(x))

  const hasFilters = active.length > 0
  const nothingInShop = !hasFilters && result.totalDocs === 0

  const hrefFor = (page: number, drop?: string) => {
    const qs = new URLSearchParams()
    if (f.q && drop !== 'q') qs.set('q', f.q)
    if (f.brand && drop !== 'brand') qs.set('brand', f.brand)
    if (f.category && drop !== 'category') qs.set('category', f.category)
    if (f.condition && drop !== 'condition') qs.set('condition', f.condition)
    if (f.sort && f.sort !== 'newest') qs.set('sort', f.sort)
    if (!f.includeSold && drop !== 'sold') qs.set('sold', '0')
    if (page > 1) qs.set('page', String(page))
    const s = qs.toString()
    return s ? `/shop?${s}` : '/shop'
  }

  const currentPage = result.page ?? 1
  const pages = Array.from({ length: result.totalPages }, (_, i) => i + 1).filter(
    (p) => p === 1 || p === result.totalPages || Math.abs(p - currentPage) <= 1,
  )

  return (
    <>
      <section className="page-hero">
        <div className="container">
          <nav className="breadcrumbs" aria-label="Breadcrumb">
            <ol>
              <li>
                <Link href="/">Home</Link>
              </li>
              <li aria-current="page">Shop</li>
            </ol>
          </nav>
          <h1>Shop the boutique</h1>
          <p className="lead">
            New and pre-loved designer bags. Every piece is one of a kind, checked by us, and held for you while you decide.
          </p>
        </div>
      </section>

      <section className="section section--tight">
        <div className="container stack" style={{ ['--stack-gap' as string]: 'var(--s-6)' }}>
          {nothingInShop ? (
            <div className="empty-state" data-reveal>
              <Icon name="bag" size={32} />
              <h2>New pieces are being photographed</h2>
              <p>
                The shop is between arrivals right now. Follow {instagram ? instagram.handle : 'us on Instagram'} to see new pieces first, or
                message us and we will tell you what is coming into the boutique.
              </p>
              <div className="cluster">
                {instagram ? (
                  <a className="btn" href={instagram.url} target="_blank" rel="noopener noreferrer">
                    <Icon name="instagram" /> Follow {instagram.handle}
                    <span className="visually-hidden">(opens Instagram)</span>
                  </a>
                ) : null}
                <Link className="btn btn--secondary" href="/contact">
                  Message us
                </Link>
                <Link className="btn btn--secondary" href="/info">
                  Selling a bag? Consign with us
                </Link>
              </div>
            </div>
          ) : (
            <>
              <form className="shop-filters" method="get" action="/shop" role="search" aria-label="Filter the shop">
                <div className="field shop-filters__q">
                  <label htmlFor="f-q">Search</label>
                  <input id="f-q" name="q" type="search" className="input" defaultValue={f.q} placeholder="Name, colour or material" maxLength={80} />
                </div>
                {options.brands.length ? (
                  <div className="field">
                    <label htmlFor="f-brand">Brand</label>
                    <select id="f-brand" name="brand" className="select" defaultValue={f.brand}>
                      <option value="">All brands</option>
                      {options.brands.filter((b) => b.slug).map((b) => (
                        <option key={b.id} value={b.slug ?? ''}>
                          {b.name}
                        </option>
                      ))}
                    </select>
                  </div>
                ) : null}
                {options.categories.length ? (
                  <div className="field">
                    <label htmlFor="f-category">Category</label>
                    <select id="f-category" name="category" className="select" defaultValue={f.category}>
                      <option value="">All categories</option>
                      {options.categories.filter((c) => c.slug).map((c) => (
                        <option key={c.id} value={c.slug ?? ''}>
                          {c.name}
                        </option>
                      ))}
                    </select>
                  </div>
                ) : null}
                {options.conditions.length ? (
                  <div className="field">
                    <label htmlFor="f-condition">Condition</label>
                    <select id="f-condition" name="condition" className="select" defaultValue={f.condition}>
                      <option value="">Any condition</option>
                      {CONDITION_OPTIONS.filter((c) => options.conditions.includes(c.value)).map((c) => (
                        <option key={c.value} value={c.value}>
                          {c.label}
                        </option>
                      ))}
                    </select>
                  </div>
                ) : null}
                <div className="field">
                  <label htmlFor="f-sort">Sort by</label>
                  <select id="f-sort" name="sort" className="select" defaultValue={f.sort}>
                    {SORTS.map((s) => (
                      <option key={s.value} value={s.value}>
                        {s.label}
                      </option>
                    ))}
                  </select>
                </div>
                <div className="shop-filters__actions">
                  {showSoldToggle ? (
                    <>
                      <input type="hidden" name="sold" value="0" />
                      <label className="checkbox">
                        <input type="checkbox" name="sold" value="1" defaultChecked={f.includeSold} />
                        <span>Include recently sold</span>
                      </label>
                    </>
                  ) : null}
                  <button type="submit" className="btn">
                    Apply
                  </button>
                </div>
              </form>

              <div className="shop-results">
                <p className="shop-results__count" role="status" aria-live="polite">
                  {result.totalDocs === 0
                    ? 'No pieces found'
                    : `${result.totalDocs} ${result.totalDocs === 1 ? 'piece' : 'pieces'}`}
                  {result.totalPages > 1 ? `, page ${currentPage} of ${result.totalPages}` : ''}
                </p>
                {hasFilters ? (
                  <ul className="shop-chips" aria-label="Active filters">
                    {active.map((a) => (
                      <li key={a.key}>
                        <Link href={hrefFor(1, a.key)} className="chip" aria-label={`Remove filter: ${a.label}`}>
                          {a.label} <Icon name="close" size={14} />
                        </Link>
                      </li>
                    ))}
                    <li>
                      <Link href="/shop" className="link-arrow">
                        Clear all
                      </Link>
                    </li>
                  </ul>
                ) : null}
              </div>

              {result.docs.length ? (
                <ul className="product-grid" style={{ listStyle: 'none', padding: 0, margin: 0 }}>
                  {result.docs.map((p, i) => (
                    <li key={p.id}>
                      <ProductCard product={p} priority={i < 4} />
                    </li>
                  ))}
                </ul>
              ) : (
                <div className="empty-state">
                  <h2>No pieces match</h2>
                  <p>Nothing in the shop fits those filters. Try a different brand or condition, or see everything we have.</p>
                  <div className="cluster">
                    <Link className="btn" href="/shop">
                      Clear all filters
                    </Link>
                    <Link className="btn btn--secondary" href="/contact">
                      Ask us to look for a piece
                    </Link>
                  </div>
                </div>
              )}

              {result.totalPages > 1 ? (
                <nav className="pagination" aria-label="Pages">
                  <ul>
                    <li>
                      {result.hasPrevPage ? (
                        <Link href={hrefFor(currentPage - 1)} rel="prev" className="btn btn--secondary btn--small">
                          <Icon name="arrow-left" /> Previous
                        </Link>
                      ) : null}
                    </li>
                    {pages.map((p, i) => (
                      <React.Fragment key={p}>
                        {i > 0 && p - pages[i - 1] > 1 ? (
                          <li aria-hidden="true" className="pagination__gap">
                            …
                          </li>
                        ) : null}
                        <li>
                          <Link
                            href={hrefFor(p)}
                            className="pagination__page"
                            aria-current={p === currentPage ? 'page' : undefined}
                            aria-label={`Page ${p}`}
                          >
                            {p}
                          </Link>
                        </li>
                      </React.Fragment>
                    ))}
                    <li>
                      {result.hasNextPage ? (
                        <Link href={hrefFor(currentPage + 1)} rel="next" className="btn btn--secondary btn--small">
                          Next <Icon name="arrow" />
                        </Link>
                      ) : null}
                    </li>
                  </ul>
                </nav>
              ) : null}
            </>
          )}
        </div>
      </section>
    </>
  )
}
