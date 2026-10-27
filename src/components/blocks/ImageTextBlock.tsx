import React from 'react'

import { MediaImage } from '@/components/MediaImage'
import { RichText } from '@/components/RichText'

import type { BlockOf } from './types'

export const ImageTextBlock = ({ block, id, tone }: { block: BlockOf<'imageText'>; id: string; tone: string }) => {
  return (
    <section
      className={`section ${tone}`}
      aria-labelledby={block.heading ? id : undefined}
    >
      <div className={`container image-text${block.imagePosition === 'right' ? ' image-text--right' : ''}`}>
        <div className="image-text__media" data-reveal>
          <MediaImage media={block.image} sizes="(min-width: 900px) 46vw, 92vw" />
        </div>
        <div className="image-text__body stack" style={{ ['--stack-gap' as string]: 'var(--s-4)' }} data-reveal>
          {block.heading ? <h2 id={id}>{block.heading}</h2> : null}
          <RichText data={block.body} />
        </div>
      </div>
    </section>
  )
}
