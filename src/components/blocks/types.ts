import type { Page } from '@/payload-types'

export type PageBlock = NonNullable<Page['layout']>[number]
export type BlockOf<T extends PageBlock['blockType']> = Extract<PageBlock, { blockType: T }>
