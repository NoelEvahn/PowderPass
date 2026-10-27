import type { GlobalConfig } from 'payload'

import { admins, anyone } from '@/access'

/** How the shop behaves: holds, payment options and delivery. Administrators only. */
export const ShopSettings: GlobalConfig = {
  slug: 'shop-settings',
  label: 'Shop settings',
  admin: {
    group: 'Settings',
    description:
      'Online payment also needs the Billplz keys in the server settings (see README). Without them, only pay-at-boutique is offered.',
  },
  access: { read: anyone, update: admins },
  versions: { max: 20 },
  fields: [
    {
      type: 'collapsible',
      label: 'Holds',
      fields: [
        {
          name: 'cartHoldHours',
          type: 'number',
          required: true,
          defaultValue: 24,
          min: 1,
          max: 24,
          label: 'Cart hold (hours)',
          admin: { description: 'How long a piece stays held after a customer adds it to their cart. Maximum 24.' },
        },
        {
          name: 'payAtShopHoldHours',
          type: 'number',
          required: true,
          defaultValue: 24,
          min: 1,
          max: 168,
          label: 'Pay-at-boutique hold (hours)',
          admin: { description: 'How long a "reserve and pay at the boutique" order holds the piece before it returns to the shop.' },
        },
        {
          name: 'paymentWindowMinutes',
          type: 'number',
          required: true,
          defaultValue: 30,
          min: 10,
          max: 120,
          label: 'Minimum time to pay online (minutes)',
          admin: { description: 'If a cart hold is nearly over at checkout, it is extended by this much so the customer can finish paying.' },
        },
        {
          name: 'maxItemsPerCart',
          type: 'number',
          required: true,
          defaultValue: 3,
          min: 1,
          max: 10,
          label: 'Most pieces one cart can hold',
        },
      ],
    },
    {
      type: 'collapsible',
      label: 'Payment',
      fields: [
        {
          name: 'onlinePaymentEnabled',
          type: 'checkbox',
          defaultValue: true,
          label: 'Offer online payment (FPX online banking, cards, Touch ’n Go and other e-wallets via Billplz)',
        },
        {
          name: 'payAtShopEnabled',
          type: 'checkbox',
          defaultValue: true,
          label: 'Offer "reserve now, pay at the boutique and collect"',
        },
      ],
    },
    {
      type: 'collapsible',
      label: 'Delivery',
      fields: [
        {
          name: 'deliveryEnabled',
          type: 'checkbox',
          defaultValue: false,
          label: 'Offer delivery at checkout',
          admin: { description: 'Only offered with online payment. Collection at the boutique is always available.' },
        },
        {
          name: 'deliveryFee',
          type: 'number',
          min: 0,
          defaultValue: 0,
          label: 'Delivery fee (RM)',
          admin: { condition: (data) => Boolean(data?.deliveryEnabled) },
        },
        {
          name: 'deliveryNote',
          type: 'text',
          maxLength: 200,
          admin: { condition: (data) => Boolean(data?.deliveryEnabled), description: 'e.g. "Same-day delivery in the Klang Valley."' },
        },
      ],
    },
    {
      type: 'collapsible',
      label: 'Shop display',
      fields: [
        {
          name: 'showSoldDays',
          type: 'number',
          defaultValue: 30,
          min: 0,
          max: 365,
          label: 'Keep sold pieces visible for (days)',
          admin: { description: 'Sold pieces stay in the shop marked "Sold" for this many days. 0 hides them straight away.' },
        },
      ],
    },
  ],
}
