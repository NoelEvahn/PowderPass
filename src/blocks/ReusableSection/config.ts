import type { Block } from 'payload'

export const ReusableSectionBlock: Block = {
  slug: 'reusableSection',
  dbName: ({ tableName }) => `${tableName}_reuse`,
  interfaceName: 'ReusableSectionBlock',
  labels: { singular: 'Reusable section', plural: 'Reusable sections' },
  fields: [
    {
      name: 'section',
      type: 'relationship',
      relationTo: 'reusable-sections',
      required: true,
      admin: { description: 'Edit the section once under "Reusable sections" and it updates everywhere it is used.' },
    },
  ],
}
