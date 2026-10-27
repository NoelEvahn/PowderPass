import React from 'react'

import { Icon } from '@/components/Icon'

import type { BlockOf } from './types'

export const NoticeBlock = ({ block }: { block: BlockOf<'notice'> }) => {
  const pending = block.tone === 'pending'
  return (
    <div className="block block--notice">
      <div className="container block-content__grid">
        <div className={`notice${pending ? ' notice--pending' : ''}`} data-reveal>
          <Icon name={pending ? 'clock' : 'info'} />
          <p>
            {pending ? <strong>Awaiting confirmation. </strong> : null}
            {block.text}
          </p>
        </div>
      </div>
    </div>
  )
}
