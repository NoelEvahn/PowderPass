import type { CollectionConfig } from 'payload'
import { canEditContent, canPublishContent } from '@/access'
import { contentBlocks } from '@/blocks'
import { revalidateAfterChange, revalidateAfterDelete } from '@/hooks/revalidate'

/** Sections maintained once and placed on many pages (e.g. the visit call-to-action). */
export const ReusableSections: CollectionConfig = {
  slug: 'reusable-sections',
  labels: { singular: 'Reusable section', plural: 'Reusable sections' },
  admin: {
    useAsTitle: 'title',
    group: 'Website',
    description: 'Edit once, update everywhere. Add to a page with the "Reusable section" block.',
  },
  access: {
    read: () => true,
    create: canPublishContent,
    update: canPublishContent,
    delete: canPublishContent,
  },
  fields: [
    { name: 'title', type: 'text', required: true, admin: { description: 'For editors only.' } },
    { name: 'layout', type: 'blocks', blocks: contentBlocks(), minRows: 1, admin: { initCollapsed: true } },
  ],
  hooks: {
    afterChange: [revalidateAfterChange],
    afterDelete: [revalidateAfterDelete],
  },
}
