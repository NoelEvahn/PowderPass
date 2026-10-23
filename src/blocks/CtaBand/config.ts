import type { Block } from 'payload'
import { linkGroup } from '@/fields/link'
import { anchorField, eyebrowField, headingField } from '../shared'

export const CtaBandBlock: Block = {
  slug: 'ctaBand',
  dbName: ({ tableName }) => `${tableName}_cta`,
  interfaceName: 'CtaBandBlock',
  labels: { singular: 'Call to action', plural: 'Calls to action' },
  fields: [
    eyebrowField,
    headingField(),
    { name: 'text', type: 'textarea' },
    { name: 'media', type: 'upload', relationTo: 'media' },
    linkGroup({ maxRows: 2 }),
    anchorField,
  ],
}
