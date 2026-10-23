import type { SerializedEditorState } from '@payloadcms/richtext-lexical/lexical'
import { type JSXConvertersFunction, LinkJSXConverter, RichText as LexicalRichText } from '@payloadcms/richtext-lexical/react'
import type { Page, Story } from '@/payload-types'
import { pageHref } from '@/lib/links'
import { STORIES_BASE_PATH } from '@/lib/routes'
import styles from './RichText.module.css'

const converters: JSXConvertersFunction = ({ defaultConverters }) => ({
  ...defaultConverters,
  ...LinkJSXConverter({
    internalDocToHref: ({ linkNode }) => {
      const doc = linkNode.fields.doc
      if (!doc || typeof doc.value !== 'object') return '#'
      return doc.relationTo === 'stories'
        ? `/${STORIES_BASE_PATH}/${(doc.value as unknown as Story).slug}`
        : pageHref((doc.value as unknown as Page).path)
    },
  }),
})

type Props = { data: SerializedEditorState | null | undefined; className?: string; size?: 'body' | 'lead' }

/** Renders CMS rich text with the site's typographic rhythm. */
export function RichText({ data, className, size = 'body' }: Props) {
  if (!data?.root?.children?.length) return null
  return (
    <LexicalRichText
      data={data}
      converters={converters}
      disableContainer={false}
      className={[styles.prose, size === 'lead' ? styles.lead : '', className].filter(Boolean).join(' ')}
    />
  )
}
