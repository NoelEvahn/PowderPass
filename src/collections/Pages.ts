import type { CollectionConfig } from 'payload'

import { editors, publishedOrStaff } from '@/access'
import { pageBlocks } from '@/blocks'
import { slugField } from '@/fields/slug'
import { getServerURL } from '@/lib/env'

const previewPath = (slug?: string | null) =>
  `${getServerURL()}/next/preview?path=${encodeURIComponent(`/${slug ?? ''}`)}`

/** Content pages built from sections: About, Info, and the policy pages. */
export const Pages: CollectionConfig = {
  slug: 'pages',
  labels: { singular: 'Page', plural: 'Pages' },
  admin: {
    useAsTitle: 'title',
    group: 'Content',
    defaultColumns: ['title', 'slug', '_status', 'updatedAt'],
    description: 'About, buying information and policy pages. Add, reorder and remove sections.',
    preview: (doc) => previewPath(doc?.slug as string),
    livePreview: {
      url: ({ data }) => previewPath(data?.slug as string),
      breakpoints: [
        { label: 'Phone', name: 'phone', width: 390, height: 844 },
        { label: 'Tablet', name: 'tablet', width: 820, height: 1180 },
        { label: 'Desktop', name: 'desktop', width: 1440, height: 900 },
      ],
    },
  },
  access: { read: publishedOrStaff, create: editors, update: editors, delete: editors },
  versions: { drafts: { autosave: { interval: 1500 } }, maxPerDoc: 30 },
  fields: [
    { name: 'title', type: 'text', required: true, maxLength: 120 },
    {
      name: 'intro',
      type: 'textarea',
      maxLength: 400,
      admin: { description: 'One or two sentences under the page title.' },
    },
    { name: 'layout', type: 'blocks', label: 'Sections', blocks: pageBlocks },
    slugField('title'),
    {
      name: 'navLabel',
      type: 'text',
      maxLength: 30,
      admin: { position: 'sidebar', description: 'Optional shorter name for menus and footer.' },
    },
    {
      name: 'lastUpdated',
      type: 'date',
      admin: { position: 'sidebar', description: 'Shown on policy pages.', date: { pickerAppearance: 'dayOnly' } },
    },
    {
      name: 'needsConfirmation',
      type: 'checkbox',
      defaultValue: false,
      label: 'Parts await confirmation',
      admin: {
        position: 'sidebar',
        description: 'Shows a visible "awaiting confirmation" notice at the top of the page.',
      },
    },
    {
      name: 'seo',
      type: 'group',
      admin: { position: 'sidebar' },
      fields: [{ name: 'description', type: 'textarea', maxLength: 170 }],
    },
  ],
}
