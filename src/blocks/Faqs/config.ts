import type { Block } from 'payload'
import { anchorField, eyebrowField, headingField, introField } from '../shared'
import { FAQ_CATEGORIES } from '@/collections/Faqs'

export const FaqsBlock: Block = {
  slug: 'faqs',
  dbName: ({ tableName }) => `${tableName}_faq_list`,
  interfaceName: 'FaqsBlock',
  labels: { singular: 'FAQ list', plural: 'FAQ lists' },
  fields: [
    eyebrowField,
    headingField(),
    introField,
    {
      name: 'source',
      type: 'radio',
      defaultValue: 'categories',
      options: [
        { label: 'By category', value: 'categories' },
        { label: 'Hand-picked questions', value: 'selected' },
      ],
      admin: { layout: 'horizontal' },
    },
    {
      name: 'categories',
      type: 'select',
      hasMany: true,
      options: [...FAQ_CATEGORIES],
      admin: { condition: (_, s) => s?.source === 'categories', description: 'Leave empty to show every published question.' },
    },
    {
      name: 'selected',
      type: 'relationship',
      relationTo: 'faqs',
      hasMany: true,
      admin: { condition: (_, s) => s?.source === 'selected' },
    },
    { name: 'searchable', type: 'checkbox', defaultValue: false, label: 'Show a filter box above the questions' },
    anchorField,
  ],
}
