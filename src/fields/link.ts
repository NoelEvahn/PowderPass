import type { ArrayField, Field, GroupField } from 'payload'

export type LinkAppearance = 'primary' | 'secondary' | 'arrow'

type LinkOptions = {
  name?: string
  label?: string
  appearances?: LinkAppearance[] | false
  required?: boolean
}

/**
 * A link that points either at a CMS page (kept in sync if the page moves)
 * or at a custom URL (external sites, documents, mailto:, tel:, #anchors).
 */
export const linkFields = ({ appearances = ['primary', 'secondary', 'arrow'], required = true }: LinkOptions = {}): Field[] => {
  const fields: Field[] = [
    {
      type: 'row',
      fields: [
        {
          name: 'type',
          type: 'radio',
          defaultValue: 'internal',
          options: [
            { label: 'Page on this site', value: 'internal' },
            { label: 'Custom URL', value: 'custom' },
          ],
          admin: { layout: 'horizontal', width: '50%' },
        },
        {
          name: 'newTab',
          type: 'checkbox',
          label: 'Open in new tab',
          admin: {
            width: '50%',
            style: { alignSelf: 'flex-end' },
            description: 'External links open in a new tab automatically.',
          },
        },
      ],
    },
    {
      type: 'row',
      fields: [
        {
          name: 'reference',
          type: 'relationship',
          relationTo: ['pages', 'stories'],
          required,
          admin: { condition: (_, s) => s?.type === 'internal', width: '50%' },
          label: 'Page',
        },
        {
          name: 'url',
          type: 'text',
          required,
          label: 'URL',
          admin: {
            condition: (_, s) => s?.type === 'custom',
            width: '50%',
            description: 'e.g. https://…, /search, mailto:info@isp.edu.my, tel:+60362808880, #section',
          },
        },
        {
          name: 'label',
          type: 'text',
          required,
          admin: { width: '50%' },
        },
      ],
    },
  ]
  if (appearances) {
    fields.push({
      name: 'appearance',
      type: 'select',
      defaultValue: appearances[0],
      options: appearances.map((value) => ({
        value,
        label: { primary: 'Primary button', secondary: 'Secondary button', arrow: 'Arrow link' }[value],
      })),
    })
  }
  return fields
}

export const link = (options: LinkOptions = {}): GroupField => ({
  name: options.name ?? 'link',
  label: options.label ?? 'Link',
  type: 'group',
  interfaceName: 'LinkField',
  fields: linkFields(options),
})

export const linkGroup = (options: LinkOptions & { maxRows?: number } = {}): ArrayField => ({
  name: options.name ?? 'links',
  label: options.label ?? 'Buttons and links',
  type: 'array',
  maxRows: options.maxRows ?? 3,
  admin: { initCollapsed: true },
  fields: [link({ ...options, name: 'link', label: 'Link' })],
})
