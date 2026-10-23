import type { Block } from 'payload'
import { anchorField } from '../shared'

export const FactStripBlock: Block = {
  slug: 'factStrip',
  dbName: ({ tableName }) => `${tableName}_facts`,
  interfaceName: 'FactStripBlock',
  labels: { singular: 'Fact strip', plural: 'Fact strips' },
  fields: [
    { name: 'heading', type: 'text' },
    {
      name: 'facts',
      type: 'array',
      minRows: 2,
      maxRows: 6,
      admin: { description: 'Verified facts only. No counters, percentages or claims without a dated source.' },
      fields: [
        { name: 'value', type: 'text', required: true },
        { name: 'label', type: 'text', required: true },
      ],
    },
    anchorField,
  ],
}
