import type { Block } from 'payload'
import { linkGroup } from '@/fields/link'
import { accentField } from '../shared'

export const HeroBlock: Block = {
  slug: 'hero',
  dbName: ({ tableName }) => `${tableName}_hero`,
  interfaceName: 'HeroBlock',
  labels: { singular: 'Hero', plural: 'Heroes' },
  fields: [
    {
      name: 'variant',
      type: 'select',
      defaultValue: 'page',
      required: true,
      options: [
        { label: 'Home — promise with image explorer', value: 'home' },
        { label: 'Page — heading with wide image', value: 'page' },
        { label: 'Learning stage — ages, accent and image', value: 'stage' },
        { label: 'Compact — heading only', value: 'compact' },
      ],
    },
    { name: 'eyebrow', type: 'text' },
    {
      name: 'heading',
      type: 'textarea',
      required: true,
      admin: { description: 'Each new line becomes its own line in the heading. This is the page’s main H1.' },
    },
    { name: 'intro', type: 'textarea' },
    linkGroup({ maxRows: 2 }),
    {
      name: 'factLine',
      type: 'text',
      admin: { description: 'Small factual line under the buttons, e.g. "Early Years · Primary · Secondary · Sixth Form".' },
    },
    {
      name: 'media',
      type: 'upload',
      relationTo: 'media',
      admin: { condition: (_, s) => s?.variant !== 'home' && s?.variant !== 'compact' },
    },
    {
      name: 'slides',
      type: 'array',
      label: 'Explorer images',
      minRows: 0,
      maxRows: 8,
      admin: {
        condition: (_, s) => s?.variant === 'home',
        description: 'Six to eight genuine school photographs. Visitors can step through them or switch to a flat list.',
      },
      fields: [
        { name: 'image', type: 'upload', relationTo: 'media', required: true },
        { name: 'caption', type: 'text', required: true },
      ],
    },
    { ...accentField('violet'), admin: { condition: (_, s) => s?.variant === 'stage' } },
    {
      name: 'stageLabel',
      type: 'text',
      admin: { condition: (_, s) => s?.variant === 'stage', description: 'e.g. "Ages 5–11 · Years 1–6"' },
    },
  ],
}
