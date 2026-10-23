import type { Field, SelectField, TextField } from 'payload'

export const ACCENTS = [
  { label: 'Deep violet (Sixth Form)', value: 'violet' },
  { label: 'Purple (Secondary)', value: 'purple' },
  { label: 'Lime (Primary)', value: 'lime' },
  { label: 'Gold (Early Years)', value: 'gold' },
] as const

export const accentField = (defaultValue = 'violet'): SelectField => ({
  name: 'accent',
  type: 'select',
  defaultValue,
  options: [...ACCENTS],
  admin: { description: 'Colour cue only. Text labels always carry the meaning.' },
})

export const eyebrowField: Field = {
  name: 'eyebrow',
  type: 'text',
  admin: { description: 'Short label shown above the heading, e.g. "Admissions".' },
}

export const headingField = (required = true): Field => ({
  name: 'heading',
  type: 'text',
  required,
})

export const introField: Field = {
  name: 'intro',
  type: 'textarea',
}

export const anchorField: TextField = {
  name: 'anchorId',
  type: 'text',
  label: 'Anchor ID',
  admin: {
    description: 'Optional. Lets links jump to this section, e.g. "learning-journey" → /#learning-journey',
  },
  validate: (value: unknown) =>
    !value || (typeof value === 'string' && /^[a-z][a-z0-9-]*$/.test(value))
      ? true
      : 'Lowercase letters, numbers and hyphens, starting with a letter.',
}

export const toneField: Field = {
  name: 'background',
  type: 'select',
  defaultValue: 'plain',
  options: [
    { label: 'White', value: 'plain' },
    { label: 'Soft violet tint', value: 'tint' },
    { label: 'Deep violet', value: 'ink' },
  ],
}
