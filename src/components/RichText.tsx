import { RichText as LexicalRichText } from '@payloadcms/richtext-lexical/react'
import type { SerializedEditorState } from '@payloadcms/richtext-lexical/lexical'
import React from 'react'

type Props = { data: unknown; className?: string }

/** Renders editor rich text as semantic HTML inside a readable measure. */
export const RichText = ({ data, className }: Props) => {
  if (!data || typeof data !== 'object' || !('root' in (data as object))) return null
  return (
    <div className={['prose', className].filter(Boolean).join(' ')}>
      <LexicalRichText data={data as SerializedEditorState} disableContainer />
    </div>
  )
}
