import type { Block } from 'payload'
import { anchorField, eyebrowField, headingField, introField, toneField } from '../shared'

export const ListColumnsBlock: Block = {
  slug: 'listColumns',
  dbName: ({ tableName }) => `${tableName}_lists`,
  interfaceName: 'ListColumnsBlock',
  labels: { singular: 'Grouped lists', plural: 'Grouped lists' },
  fields: [
    eyebrowField,
    headingField(),
    introField,
    {
      name: 'groups',
      type: 'array',
      minRows: 1,
      maxRows: 6,
      fields: [
        { name: 'title', type: 'text' },
        { name: 'items', type: 'textarea', required: true, admin: { description: 'One item per line.' } },
        { name: 'note', type: 'text' },
      ],
    },
    toneField,
    anchorField,
  ],
}
