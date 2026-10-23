import type { Block } from 'payload'
import { linkGroup } from '@/fields/link'
import { anchorField, eyebrowField, headingField, toneField } from '../shared'

export const SplitFeatureBlock: Block = {
  slug: 'splitFeature',
  dbName: ({ tableName }) => `${tableName}_split`,
  interfaceName: 'SplitFeatureBlock',
  labels: { singular: 'Image and text', plural: 'Image and text' },
  fields: [
    eyebrowField,
    headingField(),
    { name: 'body', type: 'richText' },
    { name: 'media', type: 'upload', relationTo: 'media', required: true },
    {
      name: 'mediaPosition',
      type: 'radio',
      defaultValue: 'right',
      options: [
        { label: 'Image left', value: 'left' },
        { label: 'Image right', value: 'right' },
      ],
      admin: { layout: 'horizontal' },
    },
    toneField,
    linkGroup({ maxRows: 3 }),
    anchorField,
  ],
}
