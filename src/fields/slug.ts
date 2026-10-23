import type { Field, FieldHook } from 'payload'

export const slugify = (value: string): string =>
  value
    .toLowerCase()
    .normalize('NFKD')
    .replace(/[̀-ͯ]/g, '')
    .replace(/&/g, ' and ')
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')

const formatSlug =
  (fallbackField: string): FieldHook =>
  ({ value, data, originalDoc, operation }) => {
    if (typeof value === 'string' && value.trim()) return slugify(value)
    if (operation === 'create' || !originalDoc?.slug) {
      const source = data?.[fallbackField] ?? originalDoc?.[fallbackField]
      if (typeof source === 'string') return slugify(source)
    }
    return value
  }

export const slugField = (fallbackField = 'title'): Field => ({
  name: 'slug',
  type: 'text',
  index: true,
  required: true,
  admin: {
    position: 'sidebar',
    description: 'The last part of the web address. Generated from the title if left empty; lowercase letters, numbers and hyphens only.',
  },
  hooks: { beforeValidate: [formatSlug(fallbackField)] },
  validate: (value: unknown) =>
    typeof value === 'string' && /^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(value)
      ? true
      : 'Use lowercase letters, numbers and single hyphens only.',
})
