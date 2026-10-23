import type { Block } from 'payload'
import { link } from '@/fields/link'
import { anchorField, eyebrowField, headingField, introField } from '../shared'

export const StepsBlock: Block = {
  slug: 'steps',
  dbName: ({ tableName }) => `${tableName}_steps`,
  interfaceName: 'StepsBlock',
  labels: { singular: 'Numbered steps', plural: 'Numbered steps' },
  fields: [
    eyebrowField,
    headingField(),
    introField,
    {
      name: 'layout',
      type: 'select',
      defaultValue: 'stack',
      options: [
        { label: 'Numbered list', value: 'stack' },
        { label: 'Sideways row (turns sideways as visitors scroll on large screens; swipe on phones)', value: 'horizontal' },
      ],
    },
    {
      name: 'steps',
      type: 'array',
      minRows: 1,
      maxRows: 8,
      fields: [
        { name: 'title', type: 'text', required: true },
        { name: 'body', type: 'textarea', required: true },
        link({ appearances: false, required: false }),
      ],
    },
    anchorField,
  ],
}
