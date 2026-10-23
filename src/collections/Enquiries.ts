import type { CollectionConfig } from 'payload'
import { canManageAdmissions, isAdmin } from '@/access'

export const ENQUIRY_TYPES = [
  { label: 'Admissions enquiry', value: 'enquiry' },
  { label: 'Visit / tour request', value: 'visit' },
  { label: 'Open day interest', value: 'open-day' },
  { label: 'General contact', value: 'contact' },
] as const

export const STAGE_OPTIONS = [
  { label: 'Early Years (ages 3–5)', value: 'early-years' },
  { label: 'Primary (ages 5–11)', value: 'primary' },
  { label: 'Secondary (ages 11–16)', value: 'secondary' },
  { label: 'Sixth Form (ages 16–18)', value: 'sixth-form' },
  { label: 'Not sure yet', value: 'unsure' },
] as const

/**
 * Enquiries submitted through website forms. They are created only by the
 * server-side form handler (overrideAccess); nobody can create them via the API.
 */
export const Enquiries: CollectionConfig = {
  slug: 'enquiries',
  labels: { singular: 'Enquiry', plural: 'Enquiries' },
  admin: {
    useAsTitle: 'parentName',
    defaultColumns: ['parentName', 'type', 'stage', 'status', 'createdAt'],
    group: 'Admissions',
    description: 'Messages from the website forms. Contains personal data — handle according to the school privacy policy.',
    listSearchableFields: ['parentName', 'email'],
  },
  defaultSort: '-createdAt',
  access: {
    create: () => false,
    read: canManageAdmissions,
    update: canManageAdmissions,
    delete: isAdmin,
  },
  fields: [
    {
      type: 'row',
      fields: [
        { name: 'type', type: 'select', required: true, options: [...ENQUIRY_TYPES], admin: { width: '50%' } },
        {
          name: 'status',
          type: 'select',
          defaultValue: 'new',
          options: [
            { label: 'New', value: 'new' },
            { label: 'In progress', value: 'in-progress' },
            { label: 'Responded', value: 'responded' },
            { label: 'Closed', value: 'closed' },
          ],
          admin: { width: '50%' },
        },
      ],
    },
    {
      type: 'row',
      fields: [
        { name: 'parentName', type: 'text', required: true, label: 'Name', admin: { width: '50%', readOnly: true } },
        { name: 'email', type: 'email', required: true, admin: { width: '50%', readOnly: true } },
      ],
    },
    {
      type: 'row',
      fields: [
        { name: 'phone', type: 'text', admin: { width: '33%', readOnly: true } },
        { name: 'stage', type: 'select', options: [...STAGE_OPTIONS], admin: { width: '33%', readOnly: true } },
        { name: 'intendedStart', type: 'text', label: 'Intended start', admin: { width: '33%', readOnly: true } },
      ],
    },
    { name: 'message', type: 'textarea', admin: { readOnly: true } },
    {
      type: 'row',
      fields: [
        { name: 'consentPrivacy', type: 'checkbox', label: 'Accepted privacy notice', admin: { width: '50%', readOnly: true } },
        { name: 'consentUpdates', type: 'checkbox', label: 'Opted in to school updates', admin: { width: '50%', readOnly: true } },
      ],
    },
    { name: 'sourcePath', type: 'text', label: 'Submitted from', admin: { readOnly: true } },
    {
      name: 'delivery',
      type: 'group',
      admin: { description: 'Whether notifications reached staff systems.' },
      fields: [
        { name: 'emailed', type: 'checkbox', admin: { readOnly: true } },
        { name: 'forwarded', type: 'checkbox', label: 'Sent to webhook', admin: { readOnly: true } },
        { name: 'error', type: 'text', admin: { readOnly: true } },
      ],
    },
    { name: 'staffNotes', type: 'textarea', admin: { description: 'Internal only.' } },
  ],
}
