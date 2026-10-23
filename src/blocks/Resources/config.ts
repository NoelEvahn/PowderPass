import type { Block } from 'payload'
import { anchorField, eyebrowField, headingField, introField } from '../shared'

export const ResourcesBlock: Block = {
  slug: 'resources',
  dbName: ({ tableName }) => `${tableName}_res`,
  interfaceName: 'ResourcesBlock',
  labels: { singular: 'Documents and resources', plural: 'Documents and resources' },
  fields: [
    eyebrowField,
    headingField(),
    introField,
    {
      name: 'items',
      type: 'array',
      minRows: 1,
      maxRows: 20,
      fields: [
        { name: 'title', type: 'text', required: true },
        { name: 'description', type: 'text' },
        {
          name: 'kind',
          type: 'select',
          defaultValue: 'document',
          options: [
            { label: 'Document', value: 'document' },
            { label: 'Website', value: 'website' },
            { label: 'Calendar', value: 'calendar' },
            { label: 'Portal / app', value: 'portal' },
            { label: 'Email', value: 'email' },
          ],
        },
        { name: 'url', type: 'text', required: true },
        { name: 'file', type: 'upload', relationTo: 'media', admin: { description: 'Optional: upload a PDF instead of linking to one.' } },
      ],
    },
    anchorField,
  ],
}
