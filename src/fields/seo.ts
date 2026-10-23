import type { Field } from 'payload'

export const seoFields: Field = {
  name: 'meta',
  label: 'Search & sharing',
  type: 'group',
  interfaceName: 'SeoMeta',
  admin: {
    description: 'How this page appears in search results and when shared. Leave blank to use the page title and introduction.',
  },
  fields: [
    {
      name: 'title',
      type: 'text',
      maxLength: 70,
      admin: { description: 'Ideally 50–60 characters. "| The International School @ ParkCity" is appended automatically.' },
    },
    {
      name: 'description',
      type: 'textarea',
      maxLength: 200,
      admin: { description: 'Ideally 120–160 characters, written for parents.' },
    },
    {
      name: 'image',
      type: 'upload',
      relationTo: 'media',
      admin: { description: 'Shown when the page is shared on social media. 1200 × 630 works best.' },
    },
    {
      name: 'noIndex',
      type: 'checkbox',
      label: 'Hide from search engines',
      defaultValue: false,
    },
  ],
}
