import 'server-only'

import type { Where } from 'payload'

import { activeHoldsFor, releaseExpired } from '@/lib/commerce/cart'
import { getShopSettings } from '@/lib/commerce/settings'
import { getPayloadClient } from '@/lib/payload'
import type { Brand, Category, Product } from '@/payload-types'

export type Availability = 'available' | 'held' | 'sold' | 'boutique'

export type ShopProduct = Product & {
  availability: Availability
  heldUntil?: string
  /** True when the hold belongs to the current visitor's cart. */
  heldByViewer?: boolean
}

export type ShopFilters = {
  q?: string
  brand?: string
  category?: string
  condition?: string
  min?: number
  max?: number
  sort?: 'newest' | 'price-asc' | 'price-desc'
  page?: number
  includeSold?: boolean
}

export const PAGE_SIZE = 24

const availabilityOf = (product: Product, hold?: { expiresAt: string; cartId?: string | null }, cartId?: string | null) => {
  if (product.status === 'sold') return { availability: 'sold' as const }
  if (hold && new Date(hold.expiresAt).getTime() > Date.now()) {
    return { availability: 'held' as const, heldUntil: hold.expiresAt, heldByViewer: Boolean(cartId && hold.cartId === cartId) }
  }
  if (product.sellOnline === false) return { availability: 'boutique' as const }
  return { availability: 'available' as const }
}

const annotate = async (products: Product[], cartId?: string | null): Promise<ShopProduct[]> => {
  const payload = await getPayloadClient()
  const holds = await activeHoldsFor(
    payload,
    products.filter((p) => p.status === 'available').map((p) => p.id),
  )
  return products.map((p) => ({ ...p, ...availabilityOf(p, holds.get(p.id), cartId) }))
}

const visibleWhere = async (includeSold: boolean): Promise<Where> => {
  const payload = await getPayloadClient()
  const settings = await getShopSettings(payload)
  const visible: Where[] = [{ status: { equals: 'available' } }]
  if (includeSold && settings.showSoldDays > 0) {
    const cutoff = new Date(Date.now() - settings.showSoldDays * 86_400_000).toISOString()
    visible.push({ and: [{ status: { equals: 'sold' } }, { soldAt: { greater_than: cutoff } }] })
  }
  return { and: [{ _status: { equals: 'published' } }, { or: visible }] }
}

const findBySlug = async <T extends 'brands' | 'categories'>(collection: T, slug?: string) => {
  if (!slug) return null
  const payload = await getPayloadClient()
  const { docs } = await payload.find({ collection, where: { slug: { equals: slug } }, limit: 1, depth: 0 })
  return (docs[0] as Brand | Category | undefined) ?? null
}

export const listProducts = async (filters: ShopFilters, cartId?: string | null) => {
  const payload = await getPayloadClient()
  await releaseExpired(payload)
  const and: Where[] = [await visibleWhere(filters.includeSold !== false)]

  const brand = await findBySlug('brands', filters.brand)
  if (filters.brand) and.push({ brand: { equals: brand?.id ?? -1 } })
  const category = await findBySlug('categories', filters.category)
  if (filters.category) and.push({ category: { equals: category?.id ?? -1 } })
  if (filters.condition) and.push({ condition: { equals: filters.condition } })
  if (typeof filters.min === 'number' && filters.min > 0) and.push({ price: { greater_than_equal: filters.min } })
  if (typeof filters.max === 'number' && filters.max > 0) and.push({ price: { less_than_equal: filters.max } })
  if (filters.q) {
    const q = filters.q.trim().slice(0, 80)
    if (q) and.push({ or: [{ title: { like: q } }, { colour: { like: q } }, { material: { like: q } }] })
  }

  const sort = filters.sort === 'price-asc' ? 'price' : filters.sort === 'price-desc' ? '-price' : '-createdAt'
  const result = await payload.find({
    collection: 'products',
    where: { and },
    sort: ['status', sort],
    depth: 1,
    limit: PAGE_SIZE,
    page: Math.max(1, filters.page ?? 1),
    draft: false,
  })
  return { ...result, docs: await annotate(result.docs, cartId), brand, category }
}

export const latestProducts = async (limit: number, cartId?: string | null) => {
  const payload = await getPayloadClient()
  await releaseExpired(payload)
  const featured = await payload.find({
    collection: 'products',
    where: { and: [await visibleWhere(false), { featured: { equals: true } }] },
    sort: '-updatedAt',
    depth: 1,
    limit,
    draft: false,
  })
  let docs = featured.docs
  if (docs.length < limit) {
    const rest = await payload.find({
      collection: 'products',
      where: { and: [await visibleWhere(false), { id: { not_in: docs.map((d) => d.id) } }] },
      sort: '-createdAt',
      depth: 1,
      limit: limit - docs.length,
      draft: false,
    })
    docs = [...docs, ...rest.docs]
  }
  return annotate(docs, cartId)
}

export const getProductBySlug = async (slug: string, { draft = false, cartId }: { draft?: boolean; cartId?: string | null } = {}) => {
  const payload = await getPayloadClient()
  await releaseExpired(payload)
  const { docs } = await payload.find({
    collection: 'products',
    where: draft ? { slug: { equals: slug } } : { and: [{ slug: { equals: slug } }, { _status: { equals: 'published' } }] },
    depth: 2,
    limit: 1,
    draft,
  })
  const product = docs[0]
  if (!product || (product.status === 'archived' && !draft)) return null
  const [annotated] = await annotate([product], cartId)
  return annotated
}

/** Brands and categories that currently have pieces in the shop, for the filters. */
export const getFilterOptions = async () => {
  const payload = await getPayloadClient()
  const { docs } = await payload.find({
    collection: 'products',
    where: await visibleWhere(true),
    depth: 0,
    limit: 1000,
    pagination: false,
    select: { brand: true, category: true, condition: true },
    draft: false,
  })
  const brandIds = new Set(docs.map((d) => (typeof d.brand === 'object' ? d.brand.id : d.brand)))
  const categoryIds = new Set(docs.map((d) => (typeof d.category === 'object' ? d.category?.id : d.category)).filter(Boolean))
  const conditions = new Set(docs.map((d) => d.condition))
  const [brands, categories] = await Promise.all([
    payload.find({ collection: 'brands', where: { id: { in: [...brandIds] } }, sort: 'name', limit: 200, pagination: false, depth: 0 }),
    payload.find({ collection: 'categories', where: { id: { in: [...categoryIds] as number[] } }, sort: 'sortOrder', limit: 100, pagination: false, depth: 0 }),
  ])
  return { brands: brands.docs, categories: categories.docs, conditions: [...conditions] }
}

export const brandName = (p: Product) => (typeof p.brand === 'object' ? p.brand.name : '')
export const coverImage = (p: Product) => p.gallery?.find((m) => typeof m === 'object') ?? null
