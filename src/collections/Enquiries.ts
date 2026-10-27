import type { CollectionConfig } from 'payload'

import { admins, nobody, sales } from '@/access'

export const ENQUIRY_TOPICS = [
  { label: 'A piece in the shop', value: 'piece' },
  { label: 'Booking a visit', value: 'visit' },
  { label: 'Consigning or selling a bag', value: 'consign' },
  { label: 'An order', value: 'order' },
  { label: 'Something else', value: 'other' },
] as const

/** Messages from the contact form. Never readable through the public API. */
export const Enquiries: CollectionConfig = {
  slug: 'enquiries',
  labels: { singular: 'Enquiry', plural: 'Enquiries' },
  admin: {
    useAsTitle: 'name',
    group: 'Shop',
    defaultColumns: ['name', 'topic', 'status', 'createdAt'],
    listSearchableFields: ['name', 'email', 'phone'],
  },
  access: { create: nobody, read: sales, update: sales, delete: admins },
  defaultSort: '-createdAt',
  fields: [
    {
      type: 'row',
      fields: [
        { name: 'name', type: 'text', required: true, maxLength: 120, admin: { readOnly: true, width: '33%' } },
        { name: 'email', type: 'email', admin: { readOnly: true, width: '33%' } },
        { name: 'phone', type: 'text', maxLength: 40, admin: { readOnly: true, width: '33%' } },
      ],
    },
    { name: 'topic', type: 'select', required: true, options: [...ENQUIRY_TOPICS], admin: { readOnly: true } },
    { name: 'product', type: 'relationship', relationTo: 'products', admin: { readOnly: true } },
    { name: 'message', type: 'textarea', required: true, maxLength: 4000, admin: { readOnly: true } },
    {
      name: 'status',
      type: 'select',
      defaultValue: 'new',
      options: [
        { label: 'New', value: 'new' },
        { label: 'Replied / in progress', value: 'in_progress' },
        { label: 'Closed', value: 'closed' },
      ],
      admin: { position: 'sidebar' },
    },
    { name: 'staffNotes', type: 'textarea' },
  ],
}
