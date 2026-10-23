import type { GlobalConfig } from 'payload'
import { canPublishContent } from '@/access'
import { link } from '@/fields/link'
import { revalidateGlobal } from '@/hooks/revalidate'

export const Header: GlobalConfig = {
  slug: 'header',
  label: 'Main menu',
  admin: { group: 'Settings', description: 'Top navigation, utility links and the primary button.' },
  access: { read: () => true, update: canPublishContent },
  fields: [
    {
      name: 'navGroups',
      label: 'Menu groups',
      type: 'array',
      maxRows: 6,
      admin: { description: 'Each group opens a panel of links. Drag to reorder.' },
      fields: [
        { name: 'label', type: 'text', required: true },
        { name: 'summary', type: 'textarea', admin: { description: 'One sentence shown in the open panel.' } },
        link({ name: 'overview', label: 'Overview link', appearances: false }),
        {
          name: 'items',
          type: 'array',
          maxRows: 10,
          fields: [link({ appearances: false }), { name: 'description', type: 'text' }],
        },
      ],
    },
    link({ name: 'cta', label: 'Primary button', appearances: false }),
    {
      name: 'utilityLinks',
      type: 'array',
      maxRows: 5,
      admin: { description: 'Quieter links above the main menu, e.g. Current families, Careers.' },
      fields: [link({ appearances: false })],
    },
  ],
  hooks: { afterChange: [revalidateGlobal] },
}
