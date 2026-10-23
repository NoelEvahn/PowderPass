import type { Block } from 'payload'
import { link } from '@/fields/link'
import { anchorField } from '../shared'

export const NoticeBlock: Block = {
  slug: 'notice',
  dbName: ({ tableName }) => `${tableName}_notice`,
  interfaceName: 'NoticeBlock',
  labels: { singular: 'Notice', plural: 'Notices' },
  fields: [
    {
      name: 'tone',
      type: 'select',
      defaultValue: 'info',
      options: [
        { label: 'Information', value: 'info' },
        { label: 'Important', value: 'important' },
        { label: 'Awaiting school verification', value: 'pending' },
      ],
    },
    { name: 'title', type: 'text', required: true },
    { name: 'body', type: 'textarea' },
    link({ appearances: false, required: false }),
    anchorField,
  ],
}
