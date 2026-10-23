import type { Block } from 'payload'
import { linkGroup } from '@/fields/link'
import { anchorField, eyebrowField, headingField, introField } from '../shared'

export const CampusesBlock: Block = {
  slug: 'campuses',
  dbName: ({ tableName }) => `${tableName}_campuses`,
  interfaceName: 'CampusesBlock',
  labels: { singular: 'Campuses and directions', plural: 'Campuses and directions' },
  fields: [
    eyebrowField,
    headingField(),
    introField,
    {
      name: 'showMaps',
      type: 'checkbox',
      defaultValue: false,
      label: 'Offer embedded maps',
      admin: { description: 'Embedded Google Maps only load after the visitor allows third-party media. A plain map link is always shown.' },
    },
    { name: 'media', type: 'upload', relationTo: 'media' },
    linkGroup({ maxRows: 2 }),
    anchorField,
  ],
}
