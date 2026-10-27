import type { CollectionConfig } from 'payload'

import { editors, editorsOrSales, publishedOrStaff } from '@/access'
import { slugField } from '@/fields/slug'
import { CONDITION_OPTIONS, INCLUDES_OPTIONS, PRODUCT_STATUS_OPTIONS } from '@/lib/catalog'
import { getServerURL } from '@/lib/env'

const previewPath = (slug?: string | null) =>
  `${getServerURL()}/next/preview?path=${encodeURIComponent(`/shop/${slug ?? ''}`)}`

/** One record per physical piece. Each piece is unique, so there is no quantity. */
export const Products: CollectionConfig = {
  slug: 'products',
  labels: { singular: 'Piece', plural: 'Stock' },
  admin: {
    useAsTitle: 'title',
    group: 'Stock',
    defaultColumns: ['title', 'brand', 'price', 'status', '_status', 'updatedAt'],
    listSearchableFields: ['title', 'slug', 'sku'],
    description:
      'Every bag or accessory you have. Save a draft any time; press Publish to show it in the shop. Mark it Sold when it leaves the boutique.',
    preview: (doc) => previewPath(doc?.slug as string),
    livePreview: {
      url: ({ data }) => previewPath(data?.slug as string),
      breakpoints: [
        { label: 'Phone', name: 'phone', width: 390, height: 844 },
        { label: 'Tablet', name: 'tablet', width: 820, height: 1180 },
        { label: 'Desktop', name: 'desktop', width: 1440, height: 900 },
      ],
    },
  },
  access: {
    read: publishedOrStaff,
    create: editors,
    update: editorsOrSales,
    delete: editors,
  },
  versions: {
    drafts: { autosave: { interval: 1500 }, schedulePublish: false },
    maxPerDoc: 30,
  },
  defaultSort: '-createdAt',
  fields: [
    {
      type: 'tabs',
      tabs: [
        {
          label: 'Piece',
          fields: [
            {
              name: 'title',
              type: 'text',
              required: true,
              maxLength: 120,
              admin: { description: 'For example "Classic Flap Medium, black caviar".' },
            },
            {
              type: 'row',
              fields: [
                { name: 'brand', type: 'relationship', relationTo: 'brands', required: true, admin: { width: '50%' } },
                { name: 'category', type: 'relationship', relationTo: 'categories', admin: { width: '50%' } },
              ],
            },
            {
              type: 'row',
              fields: [
                {
                  name: 'price',
                  type: 'number',
                  label: 'Price (RM)',
                  required: true,
                  min: 0,
                  max: 10_000_000,
                  admin: { width: '50%', step: 1 },
                },
                {
                  name: 'sku',
                  type: 'text',
                  label: 'Your reference (optional)',
                  maxLength: 40,
                  admin: { width: '50%', description: 'Internal stock code. Not shown to customers.' },
                },
              ],
            },
            {
              name: 'gallery',
              type: 'upload',
              relationTo: 'media',
              hasMany: true,
              required: true,
              minRows: 1,
              maxRows: 12,
              label: 'Photos',
              admin: { description: 'Real photos of this exact piece. The first photo is the cover. Drag to reorder.' },
            },
            {
              name: 'description',
              type: 'richText',
              admin: { description: 'Optional. A few sentences about the piece.' },
            },
          ],
        },
        {
          label: 'Condition & details',
          fields: [
            {
              name: 'condition',
              type: 'select',
              required: true,
              options: [...CONDITION_OPTIONS],
              defaultValue: 'excellent',
            },
            {
              name: 'conditionNotes',
              type: 'textarea',
              maxLength: 600,
              admin: { description: 'Be specific: corner wear, scratches on hardware, marks inside.' },
            },
            {
              name: 'includes',
              type: 'select',
              hasMany: true,
              options: [...INCLUDES_OPTIONS],
              label: 'Comes with',
            },
            {
              type: 'row',
              fields: [
                { name: 'colour', type: 'text', maxLength: 60, admin: { width: '33%' } },
                { name: 'material', type: 'text', maxLength: 80, admin: { width: '33%' } },
                { name: 'hardware', type: 'text', maxLength: 60, admin: { width: '33%' } },
              ],
            },
            {
              type: 'row',
              fields: [
                {
                  name: 'dimensions',
                  type: 'text',
                  maxLength: 80,
                  admin: { width: '50%', description: 'e.g. 25 x 15 x 7 cm' },
                },
                { name: 'year', type: 'text', maxLength: 40, label: 'Year / series', admin: { width: '50%' } },
              ],
            },
          ],
        },
        {
          label: 'Search & sharing',
          fields: [
            {
              name: 'seo',
              type: 'group',
              label: false,
              fields: [
                { name: 'title', type: 'text', maxLength: 70, admin: { description: 'Optional. Defaults to the piece name.' } },
                { name: 'description', type: 'textarea', maxLength: 170, admin: { description: 'Optional. One sentence for Google and link previews.' } },
              ],
            },
          ],
        },
      ],
    },
    slugField('title'),
    {
      name: 'status',
      type: 'select',
      // Distinct from the drafts `_status` enum, which would otherwise share this name.
      enumName: 'enum_products_stock_status',
      required: true,
      defaultValue: 'available',
      options: [...PRODUCT_STATUS_OPTIONS],
      index: true,
      admin: { position: 'sidebar', description: 'Set automatically to Sold when an online order is paid.' },
    },
    {
      name: 'sellOnline',
      type: 'checkbox',
      defaultValue: true,
      label: 'Can be bought online',
      admin: {
        position: 'sidebar',
        description: 'Untick to show the piece as "In the boutique, ask us" with no cart button.',
      },
    },
    {
      name: 'featured',
      type: 'checkbox',
      defaultValue: false,
      label: 'Show on home page',
      admin: { position: 'sidebar' },
    },
    {
      name: 'soldAt',
      type: 'date',
      index: true,
      admin: { position: 'sidebar', readOnly: true, date: { pickerAppearance: 'dayAndTime' } },
    },
  ],
  hooks: {
    beforeDelete: [
      async ({ req, id }) => {
        // Release any hold first: a hold cannot point at a piece that no longer exists.
        await req.payload.delete({ collection: 'reservations', where: { product: { equals: id } }, req })
      },
    ],
    beforeChange: [
      async ({ data, originalDoc, req, context }) => {
        // A piece with a paid order stays Sold (e.g. when an older draft is published)
        // until that order is cancelled.
        if (!context?.skipOrderSync && originalDoc?.id && originalDoc.status === 'sold' && data.status && data.status !== 'sold') {
          const { totalDocs } = await req.payload.count({
            collection: 'orders',
            where: { and: [{ 'items.product': { equals: originalDoc.id } }, { status: { in: ['paid', 'completed'] } }] },
            req,
          })
          if (totalDocs > 0) {
            data.status = 'sold'
            data.soldAt = originalDoc.soldAt
          }
        }
        if (data.status === 'sold' && originalDoc?.status !== 'sold' && !data.soldAt) {
          data.soldAt = new Date().toISOString()
        }
        if (data.status && data.status !== 'sold') data.soldAt = null
        return data
      },
    ],
  },
}
