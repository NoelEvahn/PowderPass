import Link from 'next/link'
import React from 'react'

import { Icon } from '@/components/Icon'

import { isInternalHref, isWebHref } from './link'
import type { BlockOf } from './types'

export const CtaBlock = ({ block, id }: { block: BlockOf<'cta'>; id: string }) => {
  const links = (block.links ?? []).slice(0, 2)
  return (
    <section className="section section--dark on-dark" aria-labelledby={id}>
      <div className="container cta" data-reveal>
        <div className="stack" style={{ ['--stack-gap' as string]: 'var(--s-3)' }}>
          <h2 id={id}>{block.heading}</h2>
          {block.text ? <p className="lead">{block.text}</p> : null}
        </div>
        {links.length ? (
          <div className="cluster">
            {links.map((link, i) => {
              const cls = `btn ${i === 0 ? 'btn--light' : 'btn--ghost-light'}`
              if (isInternalHref(link.url)) {
                return (
                  <Link key={link.id ?? i} href={link.url} className={cls}>
                    {link.label} {i === 0 ? <Icon name="arrow" /> : null}
                  </Link>
                )
              }
              const external = isWebHref(link.url)
              return (
                <a
                  key={link.id ?? i}
                  href={link.url}
                  className={cls}
                  {...(external ? { target: '_blank', rel: 'noopener noreferrer' } : {})}
                >
                  {link.label}
                  {external ? (
                    <>
                      <Icon name="external" /> <span className="visually-hidden">(opens in a new tab)</span>
                    </>
                  ) : null}
                </a>
              )
            })}
          </div>
        ) : null}
      </div>
    </section>
  )
}
