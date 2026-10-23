import type { Block } from 'payload'
import { link } from '@/fields/link'
import { accentField, anchorField, eyebrowField, headingField, introField } from '../shared'

export const LinkCardsBlock: Block = {
  slug: 'linkCards',
  dbName: ({ tableName }) => `${tableName}_cards`,
  interfaceName: 'LinkCardsBlock',
  labels: { singular: 'Link cards', plural: 'Link cards' },
  fields: [
    eyebrowField,
    headingField(),
    introField,
    {
      name: 'style',
      type: 'select',
      defaultValue: 'image',
      options: [
        { label: 'With images', value: 'image' },
        { label: 'Text list with accent bars', value: 'list' },
      ],
    },
    {
      name: 'layout',
      type: 'select',
      defaultValue: 'grid',
      options: [
        { label: 'Grid', value: 'grid' },
        { label: 'Sideways row (turns sideways as visitors scroll on large screens; swipe on phones)', value: 'horizontal' },
      ],
    },
    {
      name: 'cards',
      type: 'array',
      minRows: 1,
      maxRows: 12,
      fields: [
        { name: 'title', type: 'text', required: true },
        { name: 'meta', type: 'text', admin: { description: 'Small label, e.g. "Ages 3–5".' } },
        { name: 'text', type: 'textarea' },
        { name: 'media', type: 'upload', relationTo: 'media' },
        accentField('violet'),
        link({ appearances: false }),
      ],
    },
    anchorField,
  ],
}
