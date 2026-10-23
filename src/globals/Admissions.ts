import type { GlobalConfig } from 'payload'
import { canManageAdmissions } from '@/access'
import { revalidateGlobal } from '@/hooks/revalidate'

export const Admissions: GlobalConfig = {
  slug: 'admissions',
  label: 'Admissions information',
  admin: { group: 'Admissions', description: 'Fees, application link and required documents. Shown wherever an "Admissions information" section is placed.' },
  access: { read: () => true, update: canManageAdmissions },
  versions: { drafts: true, max: 20 },
  fields: [
    {
      type: 'tabs',
      tabs: [
        {
          label: 'Fees',
          fields: [
            { name: 'academicYear', type: 'text', required: true, admin: { description: 'e.g. "2026/2027"' } },
            { name: 'feeScheduleUrl', type: 'text', required: true, label: 'Official fee schedule link' },
            {
              name: 'feeTable',
              type: 'array',
              admin: { description: 'Optional. Only add amounts that finance has approved for this academic year. If empty, visitors are sent to the official schedule.' },
              fields: [
                { name: 'item', type: 'text', required: true },
                { name: 'amount', type: 'text', required: true },
                { name: 'note', type: 'text' },
              ],
            },
            { name: 'waiver', type: 'textarea', admin: { description: 'Registration fee waiver conditions.' } },
            { name: 'feesNote', type: 'textarea' },
            { name: 'reviewedAt', type: 'date', label: 'Fees last reviewed', admin: { date: { pickerAppearance: 'dayOnly' } } },
            { name: 'owner', type: 'text', defaultValue: 'Admissions & Finance' },
          ],
        },
        {
          label: 'Applying',
          fields: [
            { name: 'applicationUrl', type: 'text', required: true, label: 'Official online application link' },
            { name: 'applicationNote', type: 'textarea' },
            {
              name: 'requiredDocuments',
              type: 'array',
              fields: [{ name: 'item', type: 'text', required: true }],
            },
            { name: 'termsUrl', type: 'text', label: 'Terms & conditions of enrolment link' },
          ],
        },
      ],
    },
  ],
  hooks: { afterChange: [revalidateGlobal] },
}
