import type { CollectionConfig } from 'payload'

import { anyone, editorsOrSales, editors } from '@/access'

/** Photo library. Every image needs a description for people using screen readers. */
export const Media: CollectionConfig = {
  slug: 'media',
  labels: { singular: 'Image', plural: 'Images' },
  admin: {
    group: 'Content',
    defaultColumns: ['filename', 'alt', 'updatedAt'],
    description: 'Upload real photographs of your pieces and boutique. Large photos are resized automatically.',
  },
  access: {
    read: anyone,
    create: editorsOrSales,
    update: editorsOrSales,
    delete: editors,
  },
  fields: [
    {
      name: 'alt',
      type: 'text',
      required: true,
      maxLength: 200,
      label: 'Description (alt text)',
      admin: {
        description:
          'Describe what the photo shows, e.g. "Black quilted leather shoulder bag with gold chain, front view". Do not name customers.',
      },
    },
    {
      name: 'credit',
      type: 'text',
      maxLength: 120,
      admin: { description: 'Optional. Who took the photo, if it is not your own.' },
    },
  ],
  upload: {
    staticDir: process.env.MEDIA_DIR || 'media',
    mimeTypes: ['image/jpeg', 'image/png', 'image/webp', 'image/avif'],
    focalPoint: true,
    crop: true,
    formatOptions: { format: 'webp', options: { quality: 82 } },
    resizeOptions: { width: 2400, height: 2400, fit: 'inside', withoutEnlargement: true },
    imageSizes: [
      { name: 'thumb', width: 400, formatOptions: { format: 'webp', options: { quality: 78 } } },
      { name: 'card', width: 800, formatOptions: { format: 'webp', options: { quality: 80 } } },
      { name: 'large', width: 1400, formatOptions: { format: 'webp', options: { quality: 82 } } },
      { name: 'xlarge', width: 2200, formatOptions: { format: 'webp', options: { quality: 82 } } },
    ],
    adminThumbnail: 'thumb',
  },
}
