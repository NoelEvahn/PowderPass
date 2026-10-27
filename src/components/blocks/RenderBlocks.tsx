import React from 'react'

import { ContentBlock } from './ContentBlock'
import { CtaBlock } from './CtaBlock'
import { FaqBlock } from './FaqBlock'
import { ImageTextBlock } from './ImageTextBlock'
import { LatestStockBlock } from './LatestStockBlock'
import { NoticeBlock } from './NoticeBlock'
import { TermsBlock } from './TermsBlock'
import type { PageBlock } from './types'

/**
 * Renders a page's sections in order. Banded sections (icon lists, photos, stock)
 * alternate between sand and paper so neighbouring bands never share a background.
 */
export const RenderBlocks = ({ blocks, idPrefix = 'section' }: { blocks: PageBlock[] | null | undefined; idPrefix?: string }) => {
  if (!blocks?.length) return null
  let bands = 0
  return (
    <>
      {blocks.map((block, i) => {
        const id = `${idPrefix}-${block.id ?? i}`
        const nextTone = () => (bands++ % 2 === 0 ? 'section--tint' : 'section--surface')
        switch (block.blockType) {
          case 'content':
            return <ContentBlock key={id} block={block} id={id} />
          case 'terms':
            return <TermsBlock key={id} block={block} id={id} tone={nextTone()} />
          case 'faq':
            return <FaqBlock key={id} block={block} id={id} />
          case 'notice':
            return <NoticeBlock key={id} block={block} />
          case 'imageText':
            return <ImageTextBlock key={id} block={block} id={id} tone={nextTone()} />
          case 'cta':
            return <CtaBlock key={id} block={block} id={id} />
          case 'latestStock':
            return <LatestStockBlock key={id} block={block} id={id} tone={nextTone()} />
          default:
            return null
        }
      })}
    </>
  )
}
