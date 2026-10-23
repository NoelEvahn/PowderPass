import type { Block } from 'payload'
import { link } from '@/fields/link'
import { anchorField, eyebrowField, headingField, introField } from '../shared'

export const PeopleBlock: Block = {
  slug: 'people',
  dbName: ({ tableName }) => `${tableName}_people`,
  interfaceName: 'PeopleBlock',
  labels: { singular: 'People', plural: 'People' },
  fields: [
    eyebrowField,
    headingField(),
    introField,
    {
      name: 'people',
      type: 'array',
      minRows: 1,
      maxRows: 12,
      fields: [
        { name: 'name', type: 'text', required: true },
        { name: 'role', type: 'text', required: true },
        { name: 'image', type: 'upload', relationTo: 'media' },
        { name: 'bio', type: 'textarea' },
        link({ appearances: false, required: false }),
      ],
    },
    anchorField,
  ],
}
