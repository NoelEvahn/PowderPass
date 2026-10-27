import type { GlobalConfig } from 'payload'

import { anyone, editors } from '@/access'
import { getServerURL } from '@/lib/env'

export const HomePage: GlobalConfig = {
  slug: 'home-page',
  label: 'Home page',
  admin: {
    group: 'Content',
    preview: () => `${getServerURL()}/next/preview?path=%2F`,
    livePreview: {
      url: () => `${getServerURL()}/next/preview?path=%2F`,
      breakpoints: [
        { label: 'Phone', name: 'phone', width: 390, height: 844 },
        { label: 'Desktop', name: 'desktop', width: 1440, height: 900 },
      ],
    },
  },
  access: { read: anyone, update: editors },
  versions: { drafts: { autosave: { interval: 1500 } }, max: 20 },
  fields: [
    {
      name: 'hero',
      type: 'group',
      fields: [
        { name: 'heading', type: 'text', required: true, maxLength: 90, defaultValue: 'Designer pieces, new and pre-loved.' },
        {
          name: 'intro',
          type: 'textarea',
          maxLength: 260,
          defaultValue: 'A boutique in Ampang, Selangor. Shop online, hold a piece for up to a day, or come and see it in person.',
        },
        {
          name: 'image',
          type: 'upload',
          relationTo: 'media',
          admin: { description: 'Optional. A real photo of the boutique or your pieces. Without one, the address card shows instead.' },
        },
      ],
    },
    {
      name: 'highlights',
      type: 'array',
      maxRows: 4,
      labels: { singular: 'Fact', plural: 'Facts strip' },
      admin: { description: 'Three or four short, true facts shown under the hero.' },
      fields: [
        { name: 'title', type: 'text', required: true, maxLength: 50 },
        { name: 'text', type: 'text', maxLength: 110 },
      ],
    },
    {
      name: 'stockHeading',
      type: 'text',
      defaultValue: 'New in the boutique',
      maxLength: 80,
    },
    {
      name: 'about',
      type: 'group',
      label: 'About teaser',
      fields: [
        { name: 'heading', type: 'text', maxLength: 90, defaultValue: 'What is Label Trunc?' },
        { name: 'body', type: 'textarea', maxLength: 700 },
      ],
    },
    {
      name: 'consign',
      type: 'group',
      label: 'Consignment teaser',
      fields: [
        { name: 'enabled', type: 'checkbox', defaultValue: true },
        { name: 'heading', type: 'text', maxLength: 90, defaultValue: 'Selling a designer bag?' },
        { name: 'body', type: 'textarea', maxLength: 400 },
      ],
    },
  ],
}
