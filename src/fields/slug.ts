import type { Field, FieldHook } from 'payload'

export const slugify = (value: string): string =>
  value
    .normalize('NFKD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .replace(/&/g, ' and ')
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 96)

const formatSlug =
  (fallback: string): FieldHook =>
  ({ value, data, originalDoc }) => {
    if (typeof value === 'string' && value.trim()) return slugify(value)
    const source = data?.[fallback] ?? originalDoc?.[fallback]
    if (typeof source === 'string' && source.trim()) return slugify(source)
    return value
  }

/** A unique, URL-safe slug that fills itself from another field when left empty. */
export const slugField = (fallback = 'title', overrides: Partial<Field> = {}): Field =>
  ({
    name: 'slug',
    type: 'text',
    index: true,
    unique: true,
    label: 'Web address (slug)',
    admin: {
      position: 'sidebar',
      description: 'Leave empty to create it from the name. Changing it changes the page link.',
    },
    hooks: { beforeValidate: [formatSlug(fallback)] },
    ...overrides,
  }) as Field
