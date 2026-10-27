import type { Block } from 'payload'

/** Page sections an editor can add, reorder and remove. Renderers live in src/components/blocks. */

export const ContentBlock: Block = {
  slug: 'content',
  labels: { singular: 'Text', plural: 'Text sections' },
  fields: [
    { name: 'heading', type: 'text', maxLength: 120 },
    { name: 'body', type: 'richText', required: true },
    {
      name: 'width',
      type: 'select',
      defaultValue: 'narrow',
      options: [
        { label: 'Narrow (easy reading)', value: 'narrow' },
        { label: 'Wide', value: 'wide' },
      ],
    },
  ],
}

export const NoticeBlock: Block = {
  slug: 'notice',
  labels: { singular: 'Notice', plural: 'Notices' },
  fields: [
    {
      name: 'tone',
      type: 'select',
      defaultValue: 'info',
      options: [
        { label: 'Information', value: 'info' },
        { label: 'Awaiting confirmation', value: 'pending' },
      ],
    },
    { name: 'text', type: 'textarea', required: true, maxLength: 600 },
  ],
}

export const FaqBlock: Block = {
  slug: 'faq',
  labels: { singular: 'Questions & answers', plural: 'Questions & answers' },
  fields: [
    { name: 'heading', type: 'text', maxLength: 120 },
    {
      name: 'items',
      type: 'array',
      minRows: 1,
      labels: { singular: 'Question', plural: 'Questions' },
      fields: [
        { name: 'question', type: 'text', required: true, maxLength: 200 },
        { name: 'answer', type: 'richText', required: true },
      ],
    },
  ],
}

export const TermsBlock: Block = {
  slug: 'terms',
  labels: { singular: 'Icon list', plural: 'Icon lists' },
  fields: [
    { name: 'heading', type: 'text', maxLength: 120 },
    {
      name: 'items',
      type: 'array',
      minRows: 1,
      maxRows: 8,
      fields: [
        {
          name: 'icon',
          type: 'select',
          defaultValue: 'check',
          options: [
            { label: 'Tick', value: 'check' },
            { label: 'Shield (authenticity)', value: 'shield' },
            { label: 'Clock', value: 'clock' },
            { label: 'Delivery', value: 'truck' },
            { label: 'Shop', value: 'store' },
            { label: 'Card / payment', value: 'card' },
            { label: 'Message', value: 'chat' },
            { label: 'Bag', value: 'bag' },
          ],
        },
        { name: 'title', type: 'text', required: true, maxLength: 60 },
        { name: 'text', type: 'text', maxLength: 160 },
      ],
    },
  ],
}

export const ImageTextBlock: Block = {
  slug: 'imageText',
  labels: { singular: 'Photo and text', plural: 'Photo and text' },
  fields: [
    { name: 'image', type: 'upload', relationTo: 'media', required: true },
    { name: 'heading', type: 'text', maxLength: 120 },
    { name: 'body', type: 'richText' },
    {
      name: 'imagePosition',
      type: 'select',
      defaultValue: 'left',
      options: [
        { label: 'Photo on the left', value: 'left' },
        { label: 'Photo on the right', value: 'right' },
      ],
    },
  ],
}

export const CtaBlock: Block = {
  slug: 'cta',
  labels: { singular: 'Call to action', plural: 'Calls to action' },
  fields: [
    { name: 'heading', type: 'text', required: true, maxLength: 120 },
    { name: 'text', type: 'textarea', maxLength: 300 },
    {
      name: 'links',
      type: 'array',
      maxRows: 2,
      fields: [
        { name: 'label', type: 'text', required: true, maxLength: 40 },
        {
          name: 'url',
          type: 'text',
          required: true,
          admin: { description: 'A page on this site such as /shop, or a full web address.' },
        },
      ],
    },
  ],
}

export const LatestStockBlock: Block = {
  slug: 'latestStock',
  labels: { singular: 'Latest stock', plural: 'Latest stock' },
  fields: [
    { name: 'heading', type: 'text', defaultValue: 'New in', maxLength: 120 },
    { name: 'limit', type: 'number', defaultValue: 4, min: 2, max: 12 },
  ],
}

export const pageBlocks: Block[] = [
  ContentBlock,
  TermsBlock,
  FaqBlock,
  ImageTextBlock,
  NoticeBlock,
  CtaBlock,
  LatestStockBlock,
]
