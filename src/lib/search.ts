import type { CollectionBeforeChangeHook, Payload, Where } from 'payload'
import { FAQ_PAGE_PATH, STORIES_BASE_PATH } from './routes'
import { excerptAround, extractText } from './text'

export type SearchResult = {
  type: 'Page' | 'Story' | 'Question' | 'Learning stage'
  title: string
  href: string
  excerpt: string
  score: number
}

export const MIN_QUERY_LENGTH = 2
const MAX_QUERY_LENGTH = 80

export const normaliseQuery = (raw: unknown): string =>
  typeof raw === 'string' ? raw.replace(/\s+/g, ' ').trim().slice(0, MAX_QUERY_LENGTH) : ''

const published: Where = { _status: { equals: 'published' } }

const score = (title: string, body: string, term: string) => {
  const t = title.toLowerCase()
  const q = term.toLowerCase()
  if (t === q) return 100
  if (t.startsWith(q)) return 80
  if (t.includes(q)) return 60
  return body.toLowerCase().includes(q) ? 30 : 0
}

/** Searches published pages, stories, FAQs and learning stages. Public visibility rules are applied explicitly. */
export async function searchSite(payload: Payload, rawQuery: string, limit = 20): Promise<SearchResult[]> {
  const q = normaliseQuery(rawQuery)
  if (q.length < MIN_QUERY_LENGTH) return []
  const like = { like: q }

  const [pages, stories, faqs, programmes] = await Promise.all([
    payload.find({
      collection: 'pages',
      where: { and: [published, { 'meta.noIndex': { not_equals: true } }, { or: [{ title: like }, { searchText: like }] }] },
      depth: 0,
      limit: 30,
      select: { title: true, path: true, searchText: true, meta: true },
    }),
    payload.find({
      collection: 'stories',
      where: { and: [published, { or: [{ title: like }, { excerpt: like }, { searchText: like }] }] },
      depth: 0,
      limit: 15,
      select: { title: true, slug: true, excerpt: true, searchText: true },
    }),
    payload.find({
      collection: 'faqs',
      where: { and: [published, { or: [{ question: like }, { searchText: like }] }] },
      depth: 0,
      limit: 15,
      select: { question: true, searchText: true },
    }),
    payload.find({
      collection: 'programmes',
      where: { and: [published, { or: [{ name: like }, { summary: like }, { tagline: like }] }] },
      depth: 1,
      limit: 4,
      select: { name: true, summary: true, page: true },
    }),
  ])

  const results: SearchResult[] = [
    ...pages.docs.map((doc) => {
      const body = doc.searchText || doc.meta?.description || ''
      return { type: 'Page' as const, title: doc.title, href: `/${doc.path ?? ''}`, excerpt: excerptAround(body, q), score: score(doc.title, body, q) + 5 }
    }),
    ...stories.docs.map((doc) => {
      const body = `${doc.excerpt ?? ''} ${doc.searchText ?? ''}`
      return { type: 'Story' as const, title: doc.title, href: `/${STORIES_BASE_PATH}/${doc.slug}`, excerpt: excerptAround(body, q), score: score(doc.title, body, q) }
    }),
    ...faqs.docs.map((doc) => {
      const body = doc.searchText ?? ''
      return { type: 'Question' as const, title: doc.question, href: `/${FAQ_PAGE_PATH}#faq-${doc.id}`, excerpt: excerptAround(body, q), score: score(doc.question, body, q) }
    }),
    ...programmes.docs.map((doc) => {
      const page = typeof doc.page === 'object' && doc.page ? doc.page : null
      return {
        type: 'Learning stage' as const,
        title: doc.name,
        href: page?.path ? `/${page.path}` : '/learning',
        excerpt: excerptAround(doc.summary ?? '', q),
        score: score(doc.name, doc.summary ?? '', q) + 10,
      }
    }),
  ]

  // A stage page and its programme entry point at the same URL; keep the higher-scoring one.
  const byHref = new Map<string, SearchResult>()
  for (const result of results) {
    const existing = byHref.get(result.href)
    if (!existing || existing.score < result.score) byHref.set(result.href, result)
  }
  return [...byHref.values()].sort((a, b) => b.score - a.score).slice(0, limit)
}

/** Hook helper: store a flattened copy of rich content for searching. */
export const buildSearchText = (...sources: unknown[]) => sources.map((s) => extractText(s)).join(' ').slice(0, 20_000)

/** beforeChange hook factory that keeps `searchText` in sync, including on partial updates. */
export const searchIndexHook =
  (...fields: string[]): CollectionBeforeChangeHook =>
  ({ data, originalDoc }) => ({
    ...data,
    searchText: buildSearchText(...fields.map((field) => data?.[field] ?? originalDoc?.[field])),
  })
