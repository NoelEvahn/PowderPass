import type { Block } from 'payload'
import { anchorField, eyebrowField, headingField, introField } from '../shared'

export const LatestStoriesBlock: Block = {
  slug: 'latestStories',
  dbName: ({ tableName }) => `${tableName}_latest`,
  interfaceName: 'LatestStoriesBlock',
  labels: { singular: 'Latest stories', plural: 'Latest stories' },
  fields: [
    eyebrowField,
    headingField(),
    introField,
    {
      name: 'category',
      type: 'select',
      options: [
        { label: 'All categories', value: 'all' },
        { label: 'News', value: 'news' },
        { label: 'Announcements', value: 'announcement' },
        { label: 'Achievements', value: 'achievement' },
        { label: 'Event recaps', value: 'event-recap' },
      ],
      defaultValue: 'all',
    },
    { name: 'limit', type: 'number', defaultValue: 3, min: 1, max: 12 },
    {
      name: 'showAllLink',
      type: 'checkbox',
      defaultValue: true,
      label: 'Show "All stories" link',
    },
    anchorField,
  ],
}
