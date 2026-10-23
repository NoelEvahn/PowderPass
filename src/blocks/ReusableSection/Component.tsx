import type { ReusableSectionBlock as Props } from '@/payload-types'
import { RenderBlocks } from '../RenderBlocks'

export function ReusableSectionBlock({ section }: Props) {
  if (!section || typeof section !== 'object') return null
  return <RenderBlocks blocks={section.layout ?? []} nested />
}
