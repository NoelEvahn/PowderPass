import type { CollectionAfterChangeHook, CollectionAfterDeleteHook, GlobalAfterChangeHook } from 'payload'
import { revalidatePath, revalidateTag } from 'next/cache'

/** Cache tag shared by every public page. Globals (menus, footer, settings) appear on all pages. */
export const SITE_TAG = 'site'

const safely = (fn: () => void, logger?: { warn: (msg: string) => void }) => {
  try {
    fn()
  } catch (error) {
    // Outside a Next.js request (seed script, CLI) there is no cache to revalidate.
    logger?.warn(`Skipped cache revalidation: ${(error as Error).message}`)
  }
}

/** Revalidate the whole public site. Content is small enough that precise invalidation is not worth the complexity. */
export const revalidateSite = (logger?: { warn: (msg: string) => void }) =>
  safely(() => {
    revalidateTag(SITE_TAG, 'max')
    revalidatePath('/', 'layout')
  }, logger)

export const revalidateAfterChange: CollectionAfterChangeHook = ({ doc, req, context }) => {
  if (!context.disableRevalidate) revalidateSite(req.payload.logger)
  return doc
}

export const revalidateAfterDelete: CollectionAfterDeleteHook = ({ doc, req, context }) => {
  if (!context.disableRevalidate) revalidateSite(req.payload.logger)
  return doc
}

export const revalidateGlobal: GlobalAfterChangeHook = ({ doc, req, context }) => {
  if (!context.disableRevalidate) revalidateSite(req.payload.logger)
  return doc
}
