import type { Block } from 'payload'
import { anchorField, eyebrowField } from '../shared'

export const RichTextBlockConfig: Block = {
  slug: 'richText',
  dbName: ({ tableName }) => `${tableName}_text`,
  interfaceName: 'RichTextBlock',
  labels: { singular: 'Text', plural: 'Text' },
  fields: [
    eyebrowField,
    { name: 'heading', type: 'text' },
    { name: 'content', type: 'richText', required: true },
    {
      name: 'layout',
      type: 'select',
      defaultValue: 'aside',
      options: [
        { label: 'Heading beside text (editorial)', value: 'aside' },
        { label: 'Single reading column', value: 'narrow' },
      ],
    },
    { name: 'signature', type: 'text', admin: { description: 'Optional name line, e.g. for a welcome message.' } },
    { name: 'signatureRole', type: 'text', admin: { condition: (_, s) => Boolean(s?.signature) } },
    { name: 'portrait', type: 'upload', relationTo: 'media', admin: { condition: (_, s) => Boolean(s?.signature) } },
    anchorField,
  ],
}
