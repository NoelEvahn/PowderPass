/**
 * Seeds the CMS with the ISP website content.
 *
 *   npm run seed            → seeds an empty database (refuses if pages exist)
 *   npm run seed -- --reset → deletes all website content first (keeps users and enquiries)
 *
 * Images are downloaded from isp.edu.my on first run and cached in .seed-cache/.
 */
import 'dotenv/config'
import config from '@payload-config'
import { getPayload, type Payload } from 'payload'
import { homePage } from './content/home'
import { discoverPages } from './content/discover'
import { learningPages } from './content/learning'
import { lifePages } from './content/life'
import { admissionsPages } from './content/admissions'
import { communityPages } from './content/community'
import { legalPages } from './content/legal'
import { EVENTS, FAQS, PROGRAMMES, STORIES } from './content/collections'
import type { PageDef, SeedContext } from './content/context'
import { seedMedia } from './media'
import { seedGlobals } from './globals'

const PAGES: PageDef[] = [homePage, ...discoverPages, ...learningPages, ...lifePages, ...admissionsPages, ...communityPages, ...legalPages]

const ctx = { disableRevalidate: true }

async function reset(payload: Payload) {
  for (const collection of ['reusable-sections', 'programmes', 'faqs', 'events', 'stories', 'pages'] as const) {
    await payload.delete({ collection, where: { id: { exists: true } }, context: ctx })
  }
  console.log('Removed existing website content.')
}

async function ensureAdmin(payload: Payload) {
  const { totalDocs } = await payload.count({ collection: 'users' })
  if (totalDocs > 0) return
  const email = process.env.SEED_ADMIN_EMAIL
  const password = process.env.SEED_ADMIN_PASSWORD
  if (!email || !password) throw new Error('Set SEED_ADMIN_EMAIL and SEED_ADMIN_PASSWORD to create the first administrator.')
  await payload.create({ collection: 'users', data: { email, password, name: 'Website Administrator', roles: ['admin'] } })
  console.log(`Created administrator ${email}. Change the password after first sign-in.`)
}

async function main() {
  const payload = await getPayload({ config })
  if (process.argv.includes('--reset')) await reset(payload)
  else if ((await payload.count({ collection: 'pages' })).totalDocs > 0) {
    console.log('Pages already exist. Run with --reset to replace website content.')
    process.exit(0)
  }

  await ensureAdmin(payload)

  console.log('Media…')
  const images = await seedMedia(payload)

  // Pass 1: create every page (without sections) so links can reference real page IDs.
  console.log('Pages…')
  const pageIds = new Map<string, number>()
  const byDepth = [...PAGES].sort((a, b) => a.path.split('/').length - b.path.split('/').length)
  for (const page of byDepth) {
    const parts = page.path.split('/').filter(Boolean)
    const parentPath = parts.slice(0, -1).join('/')
    const doc = await payload.create({
      collection: 'pages',
      data: {
        title: page.title,
        navLabel: page.navLabel,
        slug: page.path === '' ? 'home' : parts.at(-1)!,
        parent: parentPath ? pageIds.get(parentPath) : undefined,
        contentOwner: page.owner,
        lastReviewed: '2026-10-22',
        meta: { description: page.description },
        _status: 'draft',
      },
      context: ctx,
    })
    pageIds.set(page.path, doc.id)
  }

  const link = (label: string, target: string, appearance?: string) => {
    const path = target.replace(/^\//, '').replace(/\/$/, '')
    const id = target.startsWith('/') ? pageIds.get(path) : undefined
    return id
      ? { type: 'internal', reference: { relationTo: 'pages', value: id }, label, ...(appearance ? { appearance } : {}) }
      : { type: 'custom', url: target, label, newTab: /^https?:/.test(target), ...(appearance ? { appearance } : {}) }
  }

  console.log('Learning stages…')
  const programmeIds: number[] = []
  for (const prog of PROGRAMMES) {
    const doc = await payload.create({
      collection: 'programmes',
      data: {
        ...prog,
        accent: prog.accent as never,
        highlights: prog.highlights.map((text) => ({ text })),
        image: images[prog.image],
        page: pageIds.get(prog.pagePath)!,
        _status: 'published',
      } as never,
      context: ctx,
    })
    programmeIds.push(doc.id)
  }

  console.log('FAQs…')
  const faqIds = new Map<string, number>()
  for (const [index, faq] of FAQS.entries()) {
    const doc = await payload.create({
      collection: 'faqs',
      data: { question: faq.question, answer: faq.answer as never, category: faq.category as never, order: (index + 1) * 10, _status: 'published' },
      context: ctx,
    })
    faqIds.set(faq.key, doc.id)
  }

  const seedCtx: SeedContext = {
    img: (key) => images[key],
    link: (label, target, appearance) => link(label, target, appearance),
    links: (...items) => items.map(([label, target, appearance]) => ({ link: link(label, target, appearance) })),
    programmes: () => programmeIds,
    faqs: (...keys) => keys.map((k) => {
      const id = faqIds.get(k)
      if (!id) throw new Error(`Unknown FAQ key "${k}"`)
      return id
    }),
  }

  // Pass 2: add sections and publish.
  for (const page of PAGES) {
    await payload.update({
      collection: 'pages',
      id: pageIds.get(page.path)!,
      data: { layout: page.layout(seedCtx) as never, _status: 'published' },
      context: ctx,
    })
    console.log(`  ✓ /${page.path}`)
  }

  console.log('Events and stories…')
  for (const event of EVENTS) {
    await payload.create({
      collection: 'events',
      data: { ...event, campus: event.campus as never, registrationStatus: event.registrationStatus as never, _status: 'published' } as never,
      context: ctx,
    })
  }
  for (const story of STORIES) {
    const { image, ...rest } = story
    await payload.create({
      collection: 'stories',
      data: { ...rest, heroImage: images[image], _status: 'published' } as never,
      context: ctx,
    })
  }

  console.log('Reusable sections and globals…')
  await payload.create({
    collection: 'reusable-sections',
    data: {
      title: 'Visit & Enquire call to action',
      layout: [
        {
          blockType: 'ctaBand',
          eyebrow: 'Visit & Enquire',
          heading: 'Come and experience ISP.',
          text: 'Private tours are arranged in advance. Tell us a little about your family and the Admissions team will be in touch.',
          links: seedCtx.links(['Plan your visit', '/admissions/visit', 'primary'], ['How to apply', '/admissions/apply', 'secondary']),
        },
      ] as never,
    },
    context: ctx,
  })
  await seedGlobals(payload, { link, images })

  console.log(`Done. ${PAGES.length} pages, ${STORIES.length} stories, ${FAQS.length} FAQs.`)
  console.log('Restart the web server (or redeploy) so cached pages are rebuilt from the new content.')
  process.exit(0)
}

main().catch((error) => {
  console.error(error)
  process.exit(1)
})
