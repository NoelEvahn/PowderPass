import type { MarqueeBlock as Props } from '@/payload-types'
import { MarqueeTrack } from './MarqueeTrack'

export function MarqueeBlock({ label, items }: Props) {
  return <MarqueeTrack label={label} items={(items ?? []).map((i) => i.text)} />
}
