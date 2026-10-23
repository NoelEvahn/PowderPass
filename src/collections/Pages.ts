import type { CollectionAfterChangeHook, CollectionBeforeChangeHook, CollectionConfig } from 'payload'
import { canEditContent, canPublishContent, preventContributorPublish, publishedOrAuthenticated } from '@/access'
import { pageBlocks } from '@/blocks'
import { seoFields } from '@/fields/seo'
import { slugField } from '@/fields/slug'
import { searchIndexHook } from '@/lib/search'
import { revalidateAfterChange, revalidateAfterDelete } from '@/hooks/revalidate'
import { previewUrl } from '@/lib/preview'

const HOME_SLUG = 'home'

/** Build the public path from the parent chain: "learning" + "primary" → "learning/primary". "home" maps to "". */
const computePath: CollectionBeforeChangeHook = async ({ data, req, originalDoc }) => {
  const slug: string = data.slug ?? originalDoc?.slug
  const parentId = data.parent !== undefined ? data.parent : originalDoc?.parent
  if (slug === HOME_SLUG && !parentId) {
    data.path = ''
    return data
  }
  let prefix = ''
  if (parentId) {
    const id = typeof parentId === 'object' ? parentId.id : parentId
    const parent = await req.payload.findByID({ collection: 'pages', id, depth: 0, draft: true, req })
    if (parent?.path) prefix = `${parent.path}/`
  }
  data.path = `${prefix}${slug}`
  return data
}

/** Give each section a readable name in the editor (from its heading) unless an editor has named it. */
const nameSections: CollectionBeforeChangeHook = ({ data }) => {
  if (Array.isArray(data.layout)) {
    data.layout = data.layout.map((block: Record<string, unknown>) => {
      if (block.blockName) return block
      const source = [block.heading, block.title, block.eyebrow, block.label].find((v) => typeof v === 'string' && v.trim())
      return source ? { ...block, blockName: String(source).split('\n')[0].slice(0, 60) } : block
    })
  }
  return data
}

/** When a page moves, re-save its children so their paths follow. */
const updateChildPaths: CollectionAfterChangeHook = async ({ doc, previousDoc, req }) => {
  if (!previousDoc || previousDoc.path === doc.path) return doc
  const children = await req.payload.find({
    collection: 'pages',
    where: { parent: { equals: doc.id } },
    depth: 0,
    limit: 200,
    draft: true,
    req,
  })
  for (const child of children.docs) {
    await req.payload.update({ collection: 'pages', id: child.id, data: { parent: doc.id }, draft: child._status !== 'published', req })
  }
  return doc
}

export const Pages: CollectionConfig = {
  slug: 'pages',
  labels: { singular: 'Page', plural: 'Pages' },
  admin: {
    useAsTitle: 'title',
    defaultColumns: ['title', 'path', '_status', 'updatedAt'],
    group: 'Website',
    description: 'Every page on the public website. Build pages from sections and drag them to reorder.',
    listSearchableFields: ['title', 'path'],
    livePreview: {
      url: ({ data }) => previewUrl(typeof data?.path === 'string' ? data.path : ''),
      breakpoints: [
        { label: 'Mobile', name: 'mobile', width: 390, height: 844 },
        { label: 'Tablet', name: 'tablet', width: 820, height: 1180 },
        { label: 'Desktop', name: 'desktop', width: 1440, height: 900 },
      ],
    },
    preview: (data) => previewUrl(typeof data?.path === 'string' ? data.path : ''),
  },
  access: {
    read: publishedOrAuthenticated,
    create: canEditContent,
    update: canEditContent,
    delete: canPublishContent,
  },
  versions: {
    drafts: { autosave: { interval: 800 }, schedulePublish: false },
    maxPerDoc: 50,
  },
  fields: [
    { name: 'title', type: 'text', required: true },
    {
      type: 'tabs',
      tabs: [
        {
          label: 'Sections',
          fields: [
            {
              name: 'layout',
              label: 'Sections',
              labels: { singular: 'Section', plural: 'Sections' },
              type: 'blocks',
              blocks: pageBlocks(),
              admin: { initCollapsed: true, description: 'Add, remove and drag sections to reorder them.' },
            },
          ],
        },
        { label: 'Search & sharing', fields: [seoFields] },
      ],
    },
    slugField('title'),
    {
      name: 'parent',
      type: 'relationship',
      relationTo: 'pages',
      admin: { position: 'sidebar', description: 'Places this page under another in the web address and breadcrumbs.' },
      filterOptions: ({ id }) => (id ? { id: { not_equals: id } } : true),
    },
    {
      name: 'path',
      type: 'text',
      index: true,
      unique: true,
      admin: { position: 'sidebar', readOnly: true, description: 'Full web address, calculated automatically.' },
    },
    {
      name: 'navLabel',
      type: 'text',
      admin: { position: 'sidebar', description: 'Shorter name for breadcrumbs, if different from the title.' },
    },
    {
      name: 'contentOwner',
      type: 'text',
      admin: { position: 'sidebar', description: 'Who approves this content, e.g. "Admissions" or "Head of Primary".' },
    },
    {
      name: 'searchText',
      type: 'textarea',
      admin: { hidden: true },
    },
    {
      name: 'lastReviewed',
      type: 'date',
      admin: { position: 'sidebar', date: { pickerAppearance: 'dayOnly' } },
    },
  ],
  hooks: {
    beforeChange: [computePath, nameSections, searchIndexHook('layout', 'meta'), preventContributorPublish],
    afterChange: [updateChildPaths, revalidateAfterChange],
    afterDelete: [revalidateAfterDelete],
  },
}
