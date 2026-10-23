import type { Block } from 'payload'
import { anchorField, eyebrowField, headingField, introField } from '../shared'

export const LearningJourneyBlock: Block = {
  slug: 'learningJourney',
  dbName: ({ tableName }) => `${tableName}_journey`,
  interfaceName: 'LearningJourneyBlock',
  labels: { singular: 'Learning journey', plural: 'Learning journeys' },
  fields: [
    eyebrowField,
    headingField(),
    introField,
    {
      name: 'programmes',
      type: 'relationship',
      relationTo: 'programmes',
      hasMany: true,
      required: true,
      admin: { description: 'Drag to reorder. On large screens these scroll sideways; on phones they stack.' },
    },
    {
      name: 'horizontal',
      type: 'checkbox',
      label: 'Use horizontal scroll chapter on large screens',
      defaultValue: true,
    },
    { ...anchorField, defaultValue: 'learning-journey' },
  ],
}
