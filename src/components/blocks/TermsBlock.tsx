import React from 'react'

import { Icon } from '@/components/Icon'

import type { BlockOf } from './types'

export const TermsBlock = ({ block, id, tone }: { block: BlockOf<'terms'>; id: string; tone: string }) => {
  if (!block.items?.length) return null
  return (
    <section className={`section ${tone}`} aria-labelledby={block.heading ? id : undefined}>
      <div className="container stack" style={{ ['--stack-gap' as string]: 'var(--s-6)' }}>
        {block.heading ? <h2 id={id}>{block.heading}</h2> : null}
        <ul className="term-list" data-reveal>
          {block.items.map((item, i) => (
            <li key={item.id ?? i} className="term">
              <span className="term__icon">
                <Icon name={item.icon ?? 'check'} size={22} />
              </span>
              <strong className="term__title">{item.title}</strong>
              {item.text ? <span className="term__text">{item.text}</span> : null}
            </li>
          ))}
        </ul>
      </div>
    </section>
  )
}
