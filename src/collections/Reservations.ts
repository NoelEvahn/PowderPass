import type { CollectionConfig } from 'payload'

import { nobody, sales } from '@/access'

/**
 * A hold on one piece. The unique index on `product` guarantees that only one
 * cart or order can hold a piece at a time, even under simultaneous clicks.
 * Created and removed only by server code; staff can view and release holds.
 */
export const Reservations: CollectionConfig = {
  slug: 'reservations',
  labels: { singular: 'Hold', plural: 'Holds' },
  admin: {
    group: 'Shop',
    useAsTitle: 'id',
    defaultColumns: ['product', 'expiresAt', 'order', 'customer'],
    description:
      'Pieces currently held in a shopping cart or by an unpaid order. Holds end automatically. Delete one to release the piece now.',
  },
  access: { read: sales, create: nobody, update: nobody, delete: sales },
  fields: [
    { name: 'product', type: 'relationship', relationTo: 'products', required: true, unique: true, index: true },
    { name: 'expiresAt', type: 'date', required: true, index: true, admin: { date: { pickerAppearance: 'dayAndTime' } } },
    { name: 'cartId', type: 'text', index: true, admin: { readOnly: true } },
    { name: 'order', type: 'relationship', relationTo: 'orders', index: true },
    { name: 'customer', type: 'relationship', relationTo: 'customers' },
  ],
}
