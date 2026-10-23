import type { CollectionConfig } from 'payload'
import { anyone, canEditContent, canPublishContent } from '@/access'

export const Media: CollectionConfig = {
  slug: 'media',
  labels: { singular: 'Image or file', plural: 'Media library' },
  admin: {
    useAsTitle: 'alt',
    defaultColumns: ['filename', 'alt', 'approval', 'updatedAt'],
    group: 'Website',
    description: 'Photographs and documents. Only use images the school has permission to publish.',
    listSearchableFields: ['alt', 'filename', 'caption'],
  },
  access: {
    read: anyone,
    create: canEditContent,
    update: canEditContent,
    delete: canPublishContent,
  },
  upload: {
    // Media is stored on disk locally; set S3_* variables to use cloud storage (see payload.config.ts).
    staticDir: 'media',
    mimeTypes: ['image/jpeg', 'image/png', 'image/webp', 'image/avif', 'image/svg+xml', 'application/pdf'],
    focalPoint: true,
    crop: true,
    adminThumbnail: 'thumbnail',
    formatOptions: { format: 'webp', options: { quality: 82 } },
    imageSizes: [
      { name: 'thumbnail', width: 400, formatOptions: { format: 'webp', options: { quality: 75 } } },
      { name: 'card', width: 800, formatOptions: { format: 'webp', options: { quality: 78 } } },
      { name: 'feature', width: 1400, formatOptions: { format: 'webp', options: { quality: 80 } } },
      { name: 'hero', width: 2200, formatOptions: { format: 'webp', options: { quality: 80 } } },
      { name: 'og', width: 1200, height: 630, position: 'centre', formatOptions: { format: 'jpeg', options: { quality: 80 } } },
    ],
  },
  fields: [
    {
      name: 'alt',
      type: 'text',
      required: true,
      label: 'Alternative text',
      admin: { description: 'Describe what is happening for people who cannot see the image. Do not name children. Write "decorative" only if it adds nothing.' },
    },
    { name: 'caption', type: 'text' },
    {
      type: 'row',
      fields: [
        {
          name: 'approval',
          type: 'select',
          defaultValue: 'pending',
          required: true,
          options: [
            { label: 'Approved for publication', value: 'approved' },
            { label: 'Awaiting school approval', value: 'pending' },
            { label: 'Needs student release', value: 'release-needed' },
          ],
          admin: { width: '50%', description: 'Tracks image rights and student photo releases.' },
        },
        { name: 'credit', type: 'text', admin: { width: '50%' } },
      ],
    },
    { name: 'sourceUrl', type: 'text', admin: { description: 'Where the original came from, for the approval register.' } },
    { name: 'releaseReference', type: 'text', admin: { description: 'Consent / release form reference, if applicable.' } },
  ],
}
