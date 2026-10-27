import type { CollectionConfig } from 'payload'

import { anyone, editors } from '@/access'
import { slugField } from '@/fields/slug'

export const Brands: CollectionConfig = {
  slug: 'brands',
  labels: { singular: 'Designer / brand', plural: 'Designers / brands' },
  admin: { useAsTitle: 'name', group: 'Stock', defaultColumns: ['name', 'slug'] },
  access: { read: anyone, create: editors, update: editors, delete: editors },
  defaultSort: 'name',
  fields: [{ name: 'name', type: 'text', required: true, unique: true, maxLength: 80 }, slugField('name')],
}
