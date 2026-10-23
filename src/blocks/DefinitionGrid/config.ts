import type { Block } from 'payload'
import { ICON_OPTIONS } from '@/lib/icon-options'
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
        {
          name: 'description',
          type: 'textarea',
          admin: { description: 'Shown in the grid display. The spiral shows only the icon, term and tagline.' },
        },
        { name: 'icon', type: 'select', options: [...ICON_OPTIONS], admin: { description: 'Optional icon shown with the term.' } },
      ],
    },
    {
      name: 'display',
      type: 'select',
      defaultValue: 'grid',
      options: [
        { label: 'Grid with descriptions', value: 'grid' },
        { label: 'Spiral (icon, term and tagline; turns as visitors scroll)', value: 'spiral' },
      ],
    },
    {
      name: 'columns',
      admin: { condition: (_, siblingData) => siblingData?.display !== 'spiral' },
      type: 'select',
      defaultValue: '4',
      options: ['2', '3', '4'].map((v) => ({ label: `${v} columns`, value: v })),
    },
    accentField('violet'),
    toneField,
    anchorField,
  ],
}
