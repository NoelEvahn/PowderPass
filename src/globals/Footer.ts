import type { GlobalConfig } from 'payload'
import { canPublishContent } from '@/access'
import { link } from '@/fields/link'
import { revalidateGlobal } from '@/hooks/revalidate'

export const Footer: GlobalConfig = {
  slug: 'footer',
  label: 'Footer',
  admin: { group: 'Settings' },
  access: { read: () => true, update: canPublishContent },
  fields: [
    {
      name: 'columns',
      type: 'array',
      maxRows: 4,
      fields: [
        { name: 'title', type: 'text', required: true },
        { name: 'links', type: 'array', fields: [link({ appearances: false })] },
      ],
    },
    { name: 'legalLinks', type: 'array', fields: [link({ appearances: false })] },
    {
      name: 'memberships',
      type: 'array',
      admin: { description: 'Examination boards and associations. Listing here is not a claim of accreditation.' },
      fields: [link({ appearances: false })],
    },
  ],
  hooks: { afterChange: [revalidateGlobal] },
}
