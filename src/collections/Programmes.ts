import type { CollectionConfig } from 'payload'
import { canEditContent, canPublishContent, preventContributorPublish, publishedOrAuthenticated } from '@/access'
import { accentField } from '@/blocks/shared'
import { slugField } from '@/fields/slug'
import { revalidateAfterChange, revalidateAfterDelete } from '@/hooks/revalidate'

/** A stage of the learning journey: Early Years, Primary, Secondary, Sixth Form. */
export const Programmes: CollectionConfig = {
  slug: 'programmes',
  labels: { singular: 'Learning stage', plural: 'Learning stages' },
  admin: {
    useAsTitle: 'name',
    defaultColumns: ['name', 'ages', 'order', '_status'],
    group: 'Academic',
    description: 'Learning stages shown in the learning journey, menus and search. Each links to its full page.',
  },
  defaultSort: 'order',
  access: {
    read: publishedOrAuthenticated,
    create: canPublishContent,
    update: canEditContent,
    delete: canPublishContent,
  },
  versions: { drafts: true, maxPerDoc: 20 },
  fields: [
    {
      type: 'row',
      fields: [
        { name: 'name', type: 'text', required: true, admin: { width: '50%' } },
        { name: 'ages', type: 'text', required: true, admin: { width: '25%', description: 'e.g. "3–5"' } },
        { name: 'order', type: 'number', required: true, defaultValue: 1, admin: { width: '25%' } },
      ],
    },
    { name: 'yearGroups', type: 'text', admin: { description: 'e.g. "Nursery and Reception"' } },
    { name: 'tagline', type: 'text', required: true, admin: { description: 'One short benefit statement.' } },
    {
      name: 'summary',
      type: 'textarea',
      required: true,
      admin: { description: '45–65 words describing this stage for parents.' },
    },
    {
      name: 'highlights',
      type: 'array',
      minRows: 1,
      maxRows: 4,
      admin: { description: 'Three short, factual highlights.' },
      fields: [{ name: 'text', type: 'text', required: true }],
    },
    { name: 'campus', type: 'select', options: [{ label: 'Main Campus', value: 'main' }, { label: 'Early Years Centre', value: 'eyc' }], required: true },
    { name: 'image', type: 'upload', relationTo: 'media', required: true },
    { name: 'page', type: 'relationship', relationTo: 'pages', required: true, admin: { description: 'The full page for this stage.' } },
    accentField('violet'),
    slugField('name'),
  ],
  hooks: {
    beforeChange: [preventContributorPublish],
    afterChange: [revalidateAfterChange],
    afterDelete: [revalidateAfterDelete],
  },
}
