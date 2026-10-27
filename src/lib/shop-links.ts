import type { SiteSetting } from '@/payload-types'

/** The boutique's main Instagram account, if one is set in site settings. */
export const instagramOf = (site: Pick<SiteSetting, 'socials'>) =>
  site.socials?.find((s) => s.platform === 'instagram') ?? null
