import type { Block } from 'payload'
import { link } from '@/fields/link'
import { anchorField, eyebrowField } from '../shared'

export const KineticStatementBlock: Block = {
  slug: 'kineticStatement',
  dbName: ({ tableName }) => `${tableName}_kinetic`,
  interfaceName: 'KineticStatementBlock',
  labels: { singular: 'Kinetic statement', plural: 'Kinetic statements' },
  fields: [
    eyebrowField,
    {
      name: 'words',
      type: 'array',
      minRows: 2,
      maxRows: 4,
      admin: { description: 'Short words or phrases that separate and settle as visitors scroll. Read as one sentence by screen readers.' },
      fields: [{ name: 'word', type: 'text', required: true }],
    },
    { name: 'supporting', type: 'textarea' },
    link({ appearances: false, required: false }),
    anchorField,
  ],
}
