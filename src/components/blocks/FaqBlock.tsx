import React from 'react'

import { RichText } from '@/components/RichText'

import type { BlockOf } from './types'

type LexNode = { type?: string; text?: string; children?: LexNode[] }
const BLOCK_TYPES = new Set(['paragraph', 'heading', 'list', 'listitem', 'quote'])

/** Plain text of rich text, for the FAQ structured data. */
const plainText = (node: LexNode | undefined): string => {
  if (!node) return ''
  if (node.type === 'text') return node.text ?? ''
  if (node.type === 'linebreak') return ' '
  const inner = (node.children ?? []).map(plainText).join('')
  return BLOCK_TYPES.has(node.type ?? '') ? `${inner} ` : inner
}
const toPlain = (data: unknown) =>
  plainText((data as { root?: LexNode } | null)?.root).replace(/\s+/g, ' ').trim()

export const FaqBlock = ({ block, id }: { block: BlockOf<'faq'>; id: string }) => {
  const items = block.items ?? []
  if (!items.length) return null
  const jsonLd = {
    '@context': 'https://schema.org',
    '@type': 'FAQPage',
    mainEntity: items
      .map((item) => ({ q: item.question, a: toPlain(item.answer) }))
      .filter((x) => x.a)
      .map((x) => ({ '@type': 'Question', name: x.q, acceptedAnswer: { '@type': 'Answer', text: x.a } })),
  }
  return (
    <section className="section" aria-labelledby={block.heading ? id : undefined}>
      <div className="container block-content__grid">
        {block.heading ? <h2 id={id}>{block.heading}</h2> : null}
        <div className="faq" data-reveal>
          {items.map((item, i) => (
            <details key={item.id ?? i} className="faq__item">
              <summary>
                <span>{item.question}</span>
              </summary>
              <div className="faq__answer">
                <RichText data={item.answer} />
              </div>
            </details>
          ))}
        </div>
      </div>
      {jsonLd.mainEntity.length ? (
        <script
          type="application/ld+json"
          dangerouslySetInnerHTML={{ __html: JSON.stringify(jsonLd).replace(/</g, '\\u003c') }}
        />
      ) : null}
    </section>
  )
}
