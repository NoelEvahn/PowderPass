import type { ImageKey } from '../images'

type Appearance = 'primary' | 'secondary' | 'arrow'

export type SeedContext = {
  img: (key: ImageKey) => number
  /** A link group: internal reference when `target` is a known page path, otherwise a custom URL. */
  link: (label: string, target: string, appearance?: Appearance) => Record<string, unknown>
  links: (...items: [string, string, Appearance?][]) => { link: Record<string, unknown> }[]
  programmes: () => number[]
  faqs: (...keys: string[]) => number[]
}

export type PageDef = {
  path: string
  title: string
  navLabel?: string
  owner: string
  description: string
  layout: (ctx: SeedContext) => Record<string, unknown>[]
}

export const cta = (ctx: SeedContext, heading = 'Come and experience ISP.', text = 'Private tours are arranged in advance. Tell us a little about your family and the Admissions team will be in touch.') => ({
  blockType: 'ctaBand',
  eyebrow: 'Visit & Enquire',
  heading,
  text,
  links: ctx.links(['Plan your visit', '/admissions/visit', 'primary'], ['How to apply', '/admissions/apply', 'secondary']),
})
