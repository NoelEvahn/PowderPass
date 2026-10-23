import type { Block } from 'payload'

export const MarqueeBlock: Block = {
  slug: 'marquee',
  dbName: ({ tableName }) => `${tableName}_marquee`,
  interfaceName: 'MarqueeBlock',
  labels: { singular: 'Moving word band', plural: 'Moving word bands' },
  fields: [
    {
      name: 'label',
      type: 'text',
      required: true,
      admin: { description: 'Read by screen readers instead of the moving words, e.g. "ISP core values".' },
    },
    {
      name: 'items',
      type: 'array',
      minRows: 3,
      maxRows: 16,
      admin: { description: 'Decorative only — never put essential information here. Visitors can pause it.' },
      fields: [{ name: 'text', type: 'text', required: true }],
    },
  ],
}
