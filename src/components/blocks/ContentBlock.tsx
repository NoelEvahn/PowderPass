import React from 'react'

import { RichText } from '@/components/RichText'

import type { BlockOf } from './types'

export const ContentBlock = ({ block, id }: { block: BlockOf<'content'>; id: string }) => {
  const wide = block.width === 'wide'
  return (
    <section
      className={`block block--content${block.heading ? '' : ' block--content-bare'}${wide ? ' block--wide' : ''}`}
      aria-labelledby={block.heading ? id : undefined}
    >
      <div className="container block-content__grid" data-reveal>
        {block.heading ? <h2 id={id}>{block.heading}</h2> : null}
        <RichText data={block.body} className={wide ? 'prose--wide' : undefined} />
      </div>
    </section>
  )
}
