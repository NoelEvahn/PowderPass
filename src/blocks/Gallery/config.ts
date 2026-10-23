import type { Block } from 'payload'
import { anchorField, eyebrowField, introField } from '../shared'

export const GalleryBlock: Block = {
  slug: 'gallery',
  dbName: ({ tableName }) => `${tableName}_gallery`,
  interfaceName: 'GalleryBlock',
  labels: { singular: 'Image gallery', plural: 'Image galleries' },
  fields: [
    eyebrowField,
    { name: 'heading', type: 'text' },
    introField,
    {
      name: 'layout',
      type: 'select',
      defaultValue: 'mosaic',
      options: [
        { label: 'Mosaic grid', value: 'mosaic' },
        { label: 'Scrolling strip', value: 'strip' },
        { label: 'Portrait posters (e.g. results graphics)', value: 'posters' },
      ],
    },
    {
      name: 'images',
      type: 'array',
      minRows: 1,
      maxRows: 30,
      admin: { description: 'Drag to reorder. Every image opens in an accessible full-screen viewer.' },
      fields: [
        { name: 'image', type: 'upload', relationTo: 'media', required: true },
        { name: 'caption', type: 'text' },
      ],
    },
    anchorField,
  ],
}
