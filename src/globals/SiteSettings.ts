import type { GlobalConfig } from 'payload'
import { isAdmin } from '@/access'
import { link } from '@/fields/link'
import { revalidateGlobal } from '@/hooks/revalidate'

export const SiteSettings: GlobalConfig = {
  slug: 'site-settings',
  label: 'School details',
  admin: { group: 'Settings', description: 'Contact details, campuses and social links used across the whole website.' },
  access: { read: () => true, update: isAdmin },
  fields: [
    {
      type: 'tabs',
      tabs: [
        {
          label: 'School',
          fields: [
            { name: 'schoolName', type: 'text', required: true, defaultValue: 'The International School @ ParkCity' },
            { name: 'shortName', type: 'text', required: true, defaultValue: 'ISP' },
            { name: 'location', type: 'text', defaultValue: 'Kuala Lumpur, Malaysia' },
            { name: 'generalEmail', type: 'email', required: true },
            { name: 'admissionsEmail', type: 'email' },
            { name: 'defaultDescription', type: 'textarea', required: true, admin: { description: 'Fallback search-result description.' } },
            { name: 'defaultShareImage', type: 'upload', relationTo: 'media' },
          ],
        },
        {
          label: 'Campuses',
          fields: [
            {
              name: 'campuses',
              type: 'array',
              minRows: 1,
              admin: { description: 'Shown in the footer, contact page and visit sections. Reconfirm before each school year.' },
              fields: [
                {
                  type: 'row',
                  fields: [
                    { name: 'name', type: 'text', required: true, admin: { width: '50%' } },
                    { name: 'key', type: 'select', required: true, options: [{ label: 'Main Campus', value: 'main' }, { label: 'Early Years Centre', value: 'eyc' }], admin: { width: '25%' } },
                    { name: 'ages', type: 'text', admin: { width: '25%', description: 'e.g. "Ages 5–18"' } },
                  ],
                },
                { name: 'address', type: 'textarea', required: true },
                {
                  name: 'phones',
                  type: 'array',
                  fields: [
                    { name: 'label', type: 'text', required: true },
                    { name: 'number', type: 'text', required: true },
                    { name: 'callsOnly', type: 'checkbox', defaultValue: false },
                  ],
                },
                { name: 'email', type: 'email' },
                { name: 'mapUrl', type: 'text', label: 'Map link' },
                { name: 'mapEmbedQuery', type: 'text', admin: { description: 'Search phrase for the embedded map, e.g. the full address.' } },
                { name: 'directions', type: 'textarea', admin: { description: 'Arrival notes for visitors. Avoid speculative transport details.' } },
                { name: 'image', type: 'upload', relationTo: 'media' },
                { name: 'lastVerified', type: 'date', admin: { date: { pickerAppearance: 'dayOnly' } } },
              ],
            },
          ],
        },
        {
          label: 'Social & external',
          fields: [
            {
              name: 'social',
              type: 'array',
              fields: [
                {
                  name: 'platform',
                  type: 'select',
                  required: true,
                  options: ['Facebook', 'Instagram', 'LinkedIn', 'YouTube'].map((v) => ({ label: v, value: v.toLowerCase() })),
                },
                { name: 'url', type: 'text', required: true },
              ],
            },
            { name: 'sisterSchool', type: 'group', fields: [{ name: 'label', type: 'text' }, { name: 'url', type: 'text' }] },
          ],
        },
        {
          label: 'Announcement',
          description: 'Optional slim banner at the top of every page, e.g. an open morning.',
          fields: [
            {
              name: 'announcement',
              type: 'group',
              fields: [
                { name: 'enabled', type: 'checkbox', defaultValue: false },
                { name: 'text', type: 'text' },
                link({ appearances: false, required: false }),
                {
                  name: 'expiresAt',
                  type: 'date',
                  admin: { description: 'The banner hides itself automatically after this moment.', date: { pickerAppearance: 'dayAndTime' } },
                },
              ],
            },
          ],
        },
      ],
    },
  ],
  hooks: { afterChange: [revalidateGlobal] },
}
