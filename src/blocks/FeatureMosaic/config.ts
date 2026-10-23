import type { Block } from 'payload'
import { link } from '@/fields/link'
import { anchorField, eyebrowField, headingField, introField } from '../shared'

export const FeatureMosaicBlock: Block = {
  slug: 'featureMosaic',
  dbName: ({ tableName }) => `${tableName}_mosaic`,
  interfaceName: 'FeatureMosaicBlock',
  labels: { singular: 'Feature mosaic', plural: 'Feature mosaics' },
  fields: [
    eyebrowField,
    headingField(),
    introField,
    {
      name: 'items',
      type: 'array',
      minRows: 2,
      maxRows: 6,
      admin: { description: 'Shown as an asymmetric grid of large and tall images.' },
      fields: [
        { name: 'media', type: 'upload', relationTo: 'media', required: true },
        { name: 'category', type: 'text' },
        { name: 'title', type: 'text', required: true },
        { name: 'text', type: 'textarea' },
        link({ appearances: false, required: false }),
      ],
    },
    anchorField,
  ],
}
