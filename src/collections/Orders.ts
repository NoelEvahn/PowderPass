import type { CollectionConfig } from 'payload'

import { admins, isCustomerUser, sales, userHasRole } from '@/access'

export const ORDER_STATUS_OPTIONS = [
  { label: 'Awaiting online payment', value: 'pending_payment' },
  { label: 'Reserved: pay at boutique', value: 'reserved' },
  { label: 'Paid', value: 'paid' },
  { label: 'Completed (collected / delivered)', value: 'completed' },
  { label: 'Cancelled', value: 'cancelled' },
  { label: 'Expired (hold ran out)', value: 'expired' },
  { label: 'Refund due', value: 'refund_due' },
] as const

export type OrderStatus = (typeof ORDER_STATUS_OPTIONS)[number]['value']

export const Orders: CollectionConfig = {
  slug: 'orders',
  labels: { singular: 'Order', plural: 'Orders' },
  admin: {
    useAsTitle: 'orderNumber',
    group: 'Shop',
    defaultColumns: ['orderNumber', 'status', 'paymentMethod', 'total', 'createdAt'],
    listSearchableFields: ['orderNumber', 'contact.email', 'contact.name', 'contact.phone'],
    description:
      'Orders from the website. Change the status as you go: mark a pay-at-boutique order Paid when the customer pays, then Completed when it is collected. Cancelling releases the piece back to the shop.',
  },
  access: {
    // Orders are only ever created by the checkout on the server.
    create: () => false,
    read: ({ req: { user } }) => {
      if (userHasRole(user, 'sales')) return true
      if (isCustomerUser(user)) return { customer: { equals: user!.id } }
      return false
    },
    update: sales,
    delete: admins,
  },
  versions: { maxPerDoc: 50 },
  defaultSort: '-createdAt',
  fields: [
    {
      type: 'row',
      fields: [
        { name: 'orderNumber', type: 'text', required: true, unique: true, index: true, admin: { readOnly: true, width: '50%' } },
        {
          name: 'status',
          type: 'select',
          required: true,
          index: true,
          options: [...ORDER_STATUS_OPTIONS],
          defaultValue: 'pending_payment',
          admin: { width: '50%' },
        },
      ],
    },
    {
      type: 'row',
      fields: [
        {
          name: 'paymentMethod',
          type: 'select',
          required: true,
          options: [
            { label: 'Online (FPX, cards, e-wallets)', value: 'online' },
            { label: 'Pay at boutique', value: 'pay_at_shop' },
          ],
          admin: { readOnly: true, width: '50%' },
        },
        {
          name: 'fulfilment',
          type: 'select',
          required: true,
          options: [
            { label: 'Collect at boutique', value: 'pickup' },
            { label: 'Delivery', value: 'delivery' },
          ],
          admin: { readOnly: true, width: '50%' },
        },
      ],
    },
    {
      name: 'holdExpiresAt',
      type: 'date',
      index: true,
      label: 'Piece held until',
      admin: { date: { pickerAppearance: 'dayAndTime' }, description: 'After this time an unpaid order expires and the piece returns to the shop.' },
    },
    {
      name: 'items',
      type: 'array',
      required: true,
      minRows: 1,
      admin: { readOnly: true },
      fields: [
        { name: 'product', type: 'relationship', relationTo: 'products' },
        { name: 'title', type: 'text', required: true },
        { name: 'brand', type: 'text' },
        { name: 'price', type: 'number', required: true },
      ],
    },
    {
      type: 'row',
      fields: [
        { name: 'subtotal', type: 'number', required: true, admin: { readOnly: true, width: '33%' } },
        { name: 'deliveryFee', type: 'number', defaultValue: 0, admin: { readOnly: true, width: '33%' } },
        { name: 'total', type: 'number', required: true, label: 'Total (RM)', admin: { readOnly: true, width: '33%' } },
      ],
    },
    {
      name: 'contact',
      type: 'group',
      fields: [
        {
          type: 'row',
          fields: [
            { name: 'name', type: 'text', required: true, admin: { width: '33%' } },
            { name: 'email', type: 'email', required: true, admin: { width: '33%' } },
            { name: 'phone', type: 'text', required: true, admin: { width: '33%' } },
          ],
        },
      ],
    },
    {
      name: 'deliveryAddress',
      type: 'group',
      admin: { condition: (data) => data?.fulfilment === 'delivery' },
      fields: [
        { name: 'line1', type: 'text', label: 'Address line 1' },
        { name: 'line2', type: 'text', label: 'Address line 2' },
        {
          type: 'row',
          fields: [
            { name: 'postcode', type: 'text', admin: { width: '33%' } },
            { name: 'city', type: 'text', admin: { width: '33%' } },
            { name: 'state', type: 'text', admin: { width: '33%' } },
          ],
        },
      ],
    },
    { name: 'customerNote', type: 'textarea', admin: { readOnly: true } },
    { name: 'staffNotes', type: 'textarea', admin: { description: 'Only staff can see this.' } },
    {
      name: 'customer',
      type: 'relationship',
      relationTo: 'customers',
      index: true,
      admin: { position: 'sidebar', readOnly: true },
    },
    {
      name: 'payment',
      type: 'group',
      admin: { position: 'sidebar' },
      fields: [
        { name: 'provider', type: 'text', admin: { readOnly: true } },
        { name: 'billId', type: 'text', index: true, admin: { readOnly: true } },
        { name: 'billUrl', type: 'text', admin: { readOnly: true } },
        { name: 'transactionId', type: 'text', admin: { readOnly: true } },
        { name: 'paidAt', type: 'date', admin: { date: { pickerAppearance: 'dayAndTime' } } },
      ],
    },
    {
      // Secret for the guest order link. Never returned by the API; server code reads it with overrideAccess.
      name: 'accessToken',
      type: 'text',
      required: true,
      index: true,
      access: { read: () => false, update: () => false },
      admin: { disabled: true },
    },
  ],
  hooks: {
    afterChange: [
      async ({ doc, previousDoc, operation, req, context }) => {
        if (operation !== 'update' || context?.skipOrderSync) return doc
        if (doc.status === previousDoc?.status) return doc
        // Staff changed the status in the editor: keep stock and holds in step.
        const { syncOrderStatusChange } = await import('@/lib/commerce/orders')
        await syncOrderStatusChange(req, doc, previousDoc?.status)
        return doc
      },
    ],
  },
}
