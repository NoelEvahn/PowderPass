import type { CollectionConfig } from 'payload'

import { anyone, editors } from '@/access'
import { slugField } from '@/fields/slug'

export const Categories: CollectionConfig = {
  slug: 'categories',
  labels: { singular: 'Category', plural: 'Categories' },
  admin: {
    useAsTitle: 'name',
    group: 'Stock',
    defaultColumns: ['name', 'sortOrder'],
    description: 'Shop filters, for example "Shoulder bags", "Top handle", "Small leather goods".',
  },
  access: { read: anyone, create: editors, update: editors, delete: editors },
  defaultSort: 'sortOrder',
  fields: [
    { name: 'name', type: 'text', required: true, unique: true, maxLength: 60 },
    slugField('name'),
    {
      name: 'sortOrder',
      type: 'number',
      defaultValue: 0,
      admin: { position: 'sidebar', description: 'Lower numbers show first.' },
    },
  ],
}
