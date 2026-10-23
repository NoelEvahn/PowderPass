import type { LearningJourneyBlock as Props, Programme } from '@/payload-types'
import { JourneyTrack, type JourneyPanel } from './JourneyTrack'

export function LearningJourneyBlock({ eyebrow, heading, intro, programmes, horizontal, anchorId, id }: Props) {
  const panels: JourneyPanel[] = (programmes ?? [])
    .filter((p): p is Programme => typeof p === 'object' && p !== null)
    .map((p) => ({
      id: String(p.id),
      name: p.name,
      ages: p.ages,
      yearGroups: p.yearGroups ?? null,
      tagline: p.tagline,
      summary: p.summary,
      highlights: (p.highlights ?? []).map((h) => h.text),
      accent: p.accent ?? 'violet',
      image: typeof p.image === 'object' ? p.image : null,
      href: typeof p.page === 'object' && p.page ? `/${p.page.path}` : '/learning',
    }))

  return (
    <JourneyTrack
      headingId={`journey-${id}`}
      anchorId={anchorId ?? undefined}
      eyebrow={eyebrow}
      heading={heading}
      intro={intro}
      panels={panels}
      horizontal={horizontal !== false}
    />
  )
}
