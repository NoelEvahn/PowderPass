import type { GlobalConfig } from 'payload'

import { adminFieldOnly, anyone, editors } from '@/access'

/** Contact details, address, hours and links, edited once and used everywhere. */
export const SiteSettings: GlobalConfig = {
  slug: 'site-settings',
  label: 'Contact & business details',
  admin: { group: 'Settings' },
  access: { read: anyone, update: editors },
  versions: { max: 20 },
  fields: [
    {
      type: 'tabs',
      tabs: [
        {
          label: 'Business',
          fields: [
            { name: 'businessName', type: 'text', required: true, defaultValue: 'Label Trunc' },
            {
              name: 'tagline',
              type: 'text',
              maxLength: 140,
              defaultValue: 'New and pre-loved designer pieces, curated in Ampang.',
            },
            {
              name: 'description',
              type: 'textarea',
              maxLength: 300,
              admin: { description: 'Used for Google and link previews.' },
            },
            {
              type: 'row',
              fields: [
                {
                  name: 'registeredName',
                  type: 'text',
                  admin: { width: '50%', description: 'Registered business or company name. Leave empty until confirmed.' },
                },
                {
                  name: 'ssmNumber',
                  type: 'text',
                  label: 'SSM registration number',
                  admin: { width: '50%', description: 'Leave empty until confirmed.' },
                },
              ],
            },
          ],
        },
        {
          label: 'Contact',
          fields: [
            {
              type: 'row',
              fields: [
                { name: 'phone', type: 'text', required: true, admin: { width: '50%', description: 'As shown, e.g. +60 12-421 1205' } },
                {
                  name: 'whatsapp',
                  type: 'text',
                  required: true,
                  admin: { width: '50%', description: 'Digits only with country code, e.g. 60124211205' },
                  validate: (value: unknown) =>
                    typeof value === 'string' && /^\d{8,15}$/.test(value) ? true : 'Digits only, 8 to 15 numbers.',
                },
              ],
            },
            { name: 'email', type: 'email', required: true },
            {
              name: 'notificationEmail',
              type: 'email',
              admin: { description: 'Where new order and enquiry alerts go. Defaults to the email above.' },
              access: { update: adminFieldOnly },
            },
            {
              name: 'socials',
              type: 'array',
              labels: { singular: 'Social link', plural: 'Social links' },
              fields: [
                {
                  type: 'row',
                  fields: [
                    {
                      name: 'platform',
                      type: 'select',
                      required: true,
                      options: ['Instagram', 'TikTok', 'Facebook', 'Xiaohongshu', 'YouTube'].map((p) => ({ label: p, value: p.toLowerCase() })),
                      admin: { width: '25%' },
                    },
                    { name: 'handle', type: 'text', required: true, admin: { width: '35%' } },
                    { name: 'url', type: 'text', required: true, admin: { width: '40%' } },
                  ],
                },
              ],
            },
          ],
        },
        {
          label: 'Address & hours',
          fields: [
            {
              name: 'address',
              type: 'group',
              fields: [
                { name: 'line1', type: 'text', required: true },
                { name: 'line2', type: 'text' },
                {
                  type: 'row',
                  fields: [
                    { name: 'postcode', type: 'text', required: true, admin: { width: '25%' } },
                    { name: 'city', type: 'text', required: true, admin: { width: '35%' } },
                    { name: 'state', type: 'text', required: true, admin: { width: '40%' } },
                  ],
                },
                { name: 'country', type: 'text', defaultValue: 'Malaysia' },
              ],
            },
            {
              name: 'mapsUrl',
              type: 'text',
              label: 'Google Maps link',
              admin: { description: 'The "Share" link from Google Maps.' },
            },
            {
              name: 'hours',
              type: 'array',
              labels: { singular: 'Opening hours row', plural: 'Opening hours' },
              admin: { description: 'Leave empty until confirmed; the site then says appointments are recommended.' },
              fields: [
                {
                  type: 'row',
                  fields: [
                    { name: 'days', type: 'text', required: true, admin: { width: '50%', description: 'e.g. Tuesday to Saturday' } },
                    { name: 'time', type: 'text', required: true, admin: { width: '50%', description: 'e.g. 11:00am – 7:00pm, or Closed' } },
                  ],
                },
              ],
            },
            {
              name: 'visitNote',
              type: 'textarea',
              maxLength: 300,
              defaultValue: 'Appointments recommended. Message us first and we will have your pieces ready.',
            },
          ],
        },
        {
          label: 'Announcement',
          fields: [
            {
              name: 'announcement',
              type: 'group',
              label: false,
              fields: [
                { name: 'enabled', type: 'checkbox', defaultValue: false, label: 'Show a banner at the top of every page' },
                { name: 'text', type: 'text', maxLength: 140 },
                { name: 'link', type: 'text', admin: { description: 'Optional, e.g. /shop' } },
                {
                  name: 'expiresAt',
                  type: 'date',
                  admin: { description: 'Optional. The banner hides itself after this time.', date: { pickerAppearance: 'dayAndTime' } },
                },
              ],
            },
          ],
        },
      ],
    },
  ],
}
