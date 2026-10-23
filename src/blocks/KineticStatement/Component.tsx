import type { KineticStatementBlock as Props } from '@/payload-types'
import { resolveLink } from '@/lib/links'
import { KineticWords } from './KineticWords'

export function KineticStatementBlock({ eyebrow, words, supporting, link, anchorId, id }: Props) {
  const resolved = resolveLink(link)
  return (
    <KineticWords
      headingId={`kinetic-${id}`}
      anchorId={anchorId ?? undefined}
      eyebrow={eyebrow}
      words={(words ?? []).map((w) => w.word)}
      supporting={supporting}
      link={resolved}
    />
  )
}
