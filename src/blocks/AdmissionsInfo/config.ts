import type { Block } from 'payload'
import { anchorField, eyebrowField, headingField, introField } from '../shared'

export const AdmissionsInfoBlock: Block = {
  slug: 'admissionsInfo',
  dbName: ({ tableName }) => `${tableName}_adm_info`,
  interfaceName: 'AdmissionsInfoBlock',
  labels: { singular: 'Admissions information', plural: 'Admissions information' },
  fields: [
    {
      name: 'show',
      type: 'select',
      required: true,
      defaultValue: 'fees',
      options: [
        { label: 'Fee schedule and waiver', value: 'fees' },
        { label: 'Application handoff and documents', value: 'apply' },
      ],
      admin: { description: 'Content comes from Globals → Admissions information, so it is always consistent.' },
    },
    eyebrowField,
    headingField(),
    introField,
    anchorField,
  ],
}
