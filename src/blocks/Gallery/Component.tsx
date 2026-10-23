import type { GalleryBlock as Props, Media as MediaDoc } from '@/payload-types'
import { Section } from '@/components/ui/Section'
import { SectionHeader } from '@/components/ui/SectionHeader'
import { GalleryGrid } from './GalleryGrid'

export function GalleryBlock({ eyebrow, heading, intro, layout, images, anchorId, id }: Props) {
  const headingId = `gallery-${id}`
  const items = (images ?? []).flatMap((item) =>
    item.image && typeof item.image === 'object'
      ? [{ id: item.id ?? String((item.image as MediaDoc).id), image: item.image as MediaDoc, caption: item.caption ?? item.image.caption ?? '' }]
      : [],
  )
  return (
    <Section id={anchorId} labelledBy={heading ? headingId : undefined}>
      <div className="container">
        <SectionHeader id={headingId} eyebrow={eyebrow} heading={heading} intro={intro} align="split" />
        <GalleryGrid items={items} layout={layout ?? 'mosaic'} label={heading ?? 'Photo gallery'} />
      </div>
    </Section>
  )
}
