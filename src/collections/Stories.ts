import type { CollectionConfig } from 'payload'
import { canEditContent, canPublishContent, preventContributorPublish, publishedOrAuthenticated } from '@/access'
import { seoFields } from '@/fields/seo'
import { slugField } from '@/fields/slug'
import { searchIndexHook } from '@/lib/search'
import { revalidateAfterChange, revalidateAfterDelete } from '@/hooks/revalidate'
import { previewUrl } from '@/lib/preview'
import { STORIES_BASE_PATH } from '@/lib/routes'

export const STORY_CATEGORIES = [
  { label: 'News', value: 'news' },
  { label: 'Announcement', value: 'announcement' },
  { label: 'Achievement', value: 'achievement' },
  { label: 'Event recap', value: 'event-recap' },
] as const


export const Stories: CollectionConfig = {
  slug: 'stories',
  labels: { singular: 'Story', plural: 'News & stories' },
  admin: {
    useAsTitle: 'title',
    defaultColumns: ['title', 'category', 'publishedAt', '_status'],
    group: 'Website',
    description: 'News, announcements and stories. Published stories appear in "Latest stories" sections and at /community/stories.',
    livePreview: { url: ({ data }) => previewUrl(`${STORIES_BASE_PATH}/${data?.slug ?? ''}`) },
    preview: (data) => previewUrl(`${STORIES_BASE_PATH}/${data?.slug ?? ''}`),
  },
  defaultSort: '-publishedAt',
  access: {
    read: publishedOrAuthenticated,
    create: canEditContent,
    update: canEditContent,
    delete: canPublishContent,
  },
  versions: { drafts: { autosave: { interval: 800 } }, maxPerDoc: 30 },
  fields: [
    { name: 'title', type: 'text', required: true },
    {
      type: 'row',
      fields: [
        { name: 'category', type: 'select', required: true, defaultValue: 'news', options: [...STORY_CATEGORIES], admin: { width: '50%' } },
        {
          name: 'publishedAt',
          type: 'date',
          required: true,
          defaultValue: () => new Date().toISOString(),
          admin: { width: '50%', date: { pickerAppearance: 'dayOnly', displayFormat: 'd MMM yyyy' } },
        },
      ],
    },
    { name: 'excerpt', type: 'textarea', required: true, maxLength: 280, admin: { description: 'One or two sentences shown on cards and in search.' } },
    { name: 'heroImage', type: 'upload', relationTo: 'media', required: true },
    { name: 'content', type: 'richText', required: true },
    {
      name: 'stages',
      type: 'relationship',
      relationTo: 'programmes',
      hasMany: true,
      admin: { position: 'sidebar', description: 'Related learning stages.' },
    },
    { name: 'authorRole', type: 'text', admin: { position: 'sidebar', description: 'e.g. "Head of PE". Avoid publishing student names.' } },
    slugField('title'),
    seoFields,
    { name: 'searchText', type: 'textarea', admin: { hidden: true } },
  ],
  hooks: {
    beforeChange: [searchIndexHook('excerpt', 'content'), preventContributorPublish],
    afterChange: [revalidateAfterChange],
    afterDelete: [revalidateAfterDelete],
  },
}
