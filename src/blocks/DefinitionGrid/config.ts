import type { Block } from 'payload'
import { accentField, anchorField, eyebrowField, headingField, introField, toneField } from '../shared'

export const DefinitionGridBlock: Block = {
  slug: 'definitionGrid',
  dbName: ({ tableName }) => `${tableName}_defs`,
  interfaceName: 'DefinitionGridBlock',
  labels: { singular: 'Terms and descriptions', plural: 'Terms and descriptions' },
  fields: [
    eyebrowField,
    headingField(),
    introField,
    {
      name: 'items',
      type: 'array',
      minRows: 1,
      maxRows: 16,
      fields: [
        { name: 'term', type: 'text', required: true },
        { name: 'tagline', type: 'text' },
        { name: 'description', type: 'textarea' },
      ],
    },
    {
      name: 'columns',
      type: 'select',
      defaultValue: '4',
      options: ['2', '3', '4'].map((v) => ({ label: `${v} columns`, value: v })),
    },
    accentField('violet'),
    toneField,
    anchorField,
  ],
}
