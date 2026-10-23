import type { Block } from 'payload'
import { anchorField, eyebrowField, introField } from '../shared'

export const DataTableBlock: Block = {
  slug: 'dataTable',
  dbName: ({ tableName }) => `${tableName}_tbl`,
  interfaceName: 'DataTableBlock',
  labels: { singular: 'Table', plural: 'Tables' },
  fields: [
    eyebrowField,
    { name: 'heading', type: 'text' },
    introField,
    { name: 'caption', type: 'text', required: true, admin: { description: 'Describes the table for screen readers.' } },
    {
      name: 'columns',
      type: 'array',
      minRows: 2,
      maxRows: 6,
      fields: [{ name: 'label', type: 'text', required: true }],
    },
    {
      name: 'rows',
      type: 'array',
      minRows: 1,
      admin: { description: 'Enter cells in the same order as the columns.' },
      fields: [
        {
          name: 'cells',
          type: 'array',
          minRows: 1,
          maxRows: 6,
          fields: [{ name: 'value', type: 'text' }],
        },
      ],
    },
    { name: 'note', type: 'textarea' },
    anchorField,
  ],
}
