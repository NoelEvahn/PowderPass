import type { CollectionConfig } from 'payload'
import { canEditAdmissionsContent, canManageAdmissions, preventContributorPublish, publishedOrAuthenticated } from '@/access'
import { searchIndexHook } from '@/lib/search'
import { revalidateAfterChange, revalidateAfterDelete } from '@/hooks/revalidate'

export const FAQ_CATEGORIES = [
  { label: 'Admissions & applying', value: 'admissions' },
  { label: 'Curriculum & languages', value: 'curriculum' },
  { label: 'School day & services', value: 'daily-life' },
  { label: 'Support & wellbeing', value: 'support' },
  { label: 'Visas & enrolment rules', value: 'visas' },
  { label: 'Community & communication', value: 'community' },
] as const

export const Faqs: CollectionConfig = {
  slug: 'faqs',
  labels: { singular: 'FAQ', plural: 'FAQs' },
  admin: {
    useAsTitle: 'question',
    defaultColumns: ['question', 'category', 'order', '_status'],
    group: 'Admissions',
    description: 'Questions shown in FAQ sections and site search.',
  },
  defaultSort: 'order',
  access: {
    read: publishedOrAuthenticated,
    create: canEditAdmissionsContent,
    update: canEditAdmissionsContent,
    delete: canManageAdmissions,
  },
  versions: { drafts: true, maxPerDoc: 20 },
  fields: [
    { name: 'question', type: 'text', required: true },
    { name: 'answer', type: 'richText', required: true },
    {
      type: 'row',
      fields: [
        { name: 'category', type: 'select', required: true, options: [...FAQ_CATEGORIES], admin: { width: '60%' } },
        { name: 'order', type: 'number', defaultValue: 10, admin: { width: '40%', description: 'Lower numbers appear first.' } },
      ],
    },
    { name: 'searchText', type: 'textarea', admin: { hidden: true } },
  ],
  hooks: {
    beforeChange: [searchIndexHook('answer'), preventContributorPublish],
    afterChange: [revalidateAfterChange],
    afterDelete: [revalidateAfterDelete],
  },
}
