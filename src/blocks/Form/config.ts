import type { Block } from 'payload'
import { anchorField, eyebrowField, headingField, introField } from '../shared'

export const FormBlock: Block = {
  slug: 'formBlock',
  dbName: ({ tableName }) => `${tableName}_form`,
  interfaceName: 'FormBlock',
  labels: { singular: 'Enquiry form', plural: 'Enquiry forms' },
  fields: [
    eyebrowField,
    headingField(),
    introField,
    {
      name: 'formType',
      type: 'select',
      required: true,
      defaultValue: 'enquiry',
      options: [
        { label: 'Admissions enquiry', value: 'enquiry' },
        { label: 'Visit / tour request', value: 'visit' },
        { label: 'Open day interest', value: 'open-day' },
        { label: 'General contact', value: 'contact' },
      ],
      admin: { description: 'Submissions are stored under Enquiries in the CMS and, if configured, emailed to admissions.' },
    },
    { name: 'aside', type: 'richText', admin: { description: 'Optional text beside the form, e.g. what happens next.' } },
    anchorField,
  ],
}
