/**
 * Seeds the facts Label Trunc has already published (contact details, address,
 * links), the editable pages and an administrator account. It never creates stock
 * or images: those come only from real photographs uploaded in the editor.
 *
 * Safe to run more than once: existing records are left as they are.
 * Run with: pnpm seed
 */
import config from '@payload-config'
import { getPayload } from 'payload'

import { pages } from './pages'

const run = async () => {
  const payload = await getPayload({ config })
  const log = (msg: string) => payload.logger.info(`[seed] ${msg}`)

  // Administrator
  const adminEmail = process.env.SEED_ADMIN_EMAIL
  const adminPassword = process.env.SEED_ADMIN_PASSWORD
  const { totalDocs: staffCount } = await payload.count({ collection: 'users' })
  if (staffCount === 0) {
    if (!adminEmail || !adminPassword) {
      log('No staff yet. Set SEED_ADMIN_EMAIL and SEED_ADMIN_PASSWORD, or create the first account at /admin.')
    } else {
      await payload.create({
        collection: 'users',
        data: { email: adminEmail, password: adminPassword, name: 'Label Trunc', roles: ['admin'] },
      })
      log(`Created administrator ${adminEmail}`)
    }
  }

  // Contact and business details: only facts already published on the previous site.
  const site = await payload.findGlobal({ slug: 'site-settings', depth: 0 })
  if (!site.phone) {
    await payload.updateGlobal({
      slug: 'site-settings',
      data: {
        businessName: 'Label Trunc',
        tagline: 'New and pre-loved designer pieces, curated in Ampang.',
        description:
          'Label Trunc is a boutique for new and pre-loved designer bags in Ampang, Selangor. 100% authentic, money-back guaranteed. Shop online or visit by appointment.',
        phone: '+60 12-421 1205',
        whatsapp: '60124211205',
        email: 'labeltrunc@gmail.com',
        socials: [
          { platform: 'instagram', handle: '@labeltrunc_luxury', url: 'https://www.instagram.com/labeltrunc_luxury/' },
          { platform: 'instagram', handle: '@labeltrunc', url: 'https://www.instagram.com/labeltrunc/' },
          { platform: 'tiktok', handle: '@labeltrunc', url: 'https://www.tiktok.com/@labeltrunc' },
          { platform: 'facebook', handle: 'labeltrunc', url: 'https://www.facebook.com/labeltrunc/' },
        ],
        address: {
          line1: '11-2A, Level 2, Ukay Boulevard',
          line2: 'Jln Lingkaran Tengah 2',
          postcode: '68000',
          city: 'Ampang',
          state: 'Selangor',
          country: 'Malaysia',
        },
        mapsUrl: 'https://goo.gl/maps/3uq3UUKJANUe7Prx8',
        hours: [],
        visitNote: 'Appointments recommended. Message us first and we will have your pieces ready.',
      },
    })
    log('Saved contact and business details')
  }

  const home = await payload.findGlobal({ slug: 'home-page', depth: 0 })
  if (!home.highlights?.length) {
    await payload.updateGlobal({
      slug: 'home-page',
      data: {
        _status: 'published',
        hero: {
          heading: 'Designer pieces, new and pre-loved.',
          intro:
            'A boutique in Ampang, Selangor. Shop online, hold a piece in your cart for up to a day, or come and see it in person.',
        },
        highlights: [
          { title: '100% authentic', text: 'Every piece is guaranteed authentic, with a money-back guarantee.' },
          { title: 'Hold it for a day', text: 'Pieces in your cart are held for you for up to 24 hours.' },
          { title: 'Pay your way', text: 'Pay online, or reserve and pay when you collect at the boutique.' },
          { title: 'Same-day delivery', text: 'Available in the Klang Valley. Ask us when you order.' },
        ],
        stockHeading: 'New in the boutique',
        about: {
          heading: 'What is Label Trunc?',
          body:
            'Label Trunc is a boutique in Ampang for new and pre-loved designer bags. Every piece is checked and guaranteed authentic. Browse what is in the boutique now, reserve online, and collect in person or have it delivered.',
        },
        consign: {
          enabled: true,
          heading: 'Selling a designer bag?',
          body: 'Send us a few photos and we will explain how consignment works before anything is agreed.',
        },
      },
    })
    log('Saved home page content')
  }

  // Shop filters used on the previous site.
  for (const [i, name] of ['Top handle', 'Shoulder'].entries()) {
    const { totalDocs } = await payload.count({ collection: 'categories', where: { name: { equals: name } } })
    if (!totalDocs) await payload.create({ collection: 'categories', data: { name, sortOrder: i } })
  }

  for (const page of pages) {
    const { totalDocs } = await payload.count({ collection: 'pages', where: { slug: { equals: page.slug } } })
    if (totalDocs) continue
    await payload.create({ collection: 'pages', data: { ...page, _status: 'published' } as never })
    log(`Created page /${page.slug}`)
  }

  log('Done')
  process.exit(0)
}

try {
  await run()
} catch (err) {
  console.error(err)
  process.exit(1)
}
