import type { Block } from 'payload'
import { anchorField, eyebrowField, headingField, introField } from '../shared'

export const EventsListBlock: Block = {
  slug: 'eventsList',
  dbName: ({ tableName }) => `${tableName}_events_list`,
  interfaceName: 'EventsListBlock',
  labels: { singular: 'Upcoming events', plural: 'Upcoming events' },
  fields: [
    eyebrowField,
    headingField(),
    introField,
    {
      name: 'campus',
      type: 'select',
      defaultValue: 'all',
      options: [
        { label: 'All campuses', value: 'all' },
        { label: 'Main Campus', value: 'main' },
        { label: 'Early Years Centre', value: 'eyc' },
      ],
    },
    { name: 'limit', type: 'number', defaultValue: 4, min: 1, max: 20 },
    {
      name: 'emptyText',
      type: 'textarea',
      defaultValue: 'There are no upcoming events published at the moment. Register your interest below and we will let you know when dates are confirmed.',
      admin: { description: 'Shown when no future events exist. Events disappear automatically after they end.' },
    },
    anchorField,
  ],
}
