import type { Access, CollectionBeforeChangeHook, FieldAccess, PayloadRequest } from 'payload'

/**
 * Roles
 * - admin:       everything, including user management and site settings.
 * - editor:      create, edit, publish and delete all website content.
 * - contributor: create and edit drafts; cannot publish, unpublish or delete.
 * - admissions:  manages enquiries, events, FAQs and admissions information.
 */
export const ROLES = ['admin', 'editor', 'contributor', 'admissions'] as const
export type Role = (typeof ROLES)[number]

type UserLike = { roles?: Role[] | null } | null | undefined

export const hasRole = (user: UserLike, ...roles: Role[]): boolean =>
  Boolean(user?.roles?.some((role) => roles.includes(role)))

const userOf = (req: PayloadRequest) => req.user as UserLike

export const anyone: Access = () => true

export const authenticated: Access = ({ req }) => Boolean(req.user)

export const isAdmin: Access = ({ req }) => hasRole(userOf(req), 'admin')

export const isAdminField: FieldAccess = ({ req }) => hasRole(userOf(req), 'admin')

export const canEditContent: Access = ({ req }) =>
  hasRole(userOf(req), 'admin', 'editor', 'contributor')

export const canPublishContent: Access = ({ req }) => hasRole(userOf(req), 'admin', 'editor')

export const canManageAdmissions: Access = ({ req }) =>
  hasRole(userOf(req), 'admin', 'editor', 'admissions')

export const canEditAdmissionsContent: Access = ({ req }) =>
  hasRole(userOf(req), 'admin', 'editor', 'contributor', 'admissions')

/** Public visitors only see published documents; signed-in staff see drafts too. */
export const publishedOrAuthenticated: Access = ({ req }) => {
  if (req.user) return true
  return { _status: { equals: 'published' } }
}

/** Users may read and update their own account; admins may manage everyone. */
export const adminOrSelf: Access = ({ req }) => {
  const user = req.user as (UserLike & { id?: number | string }) | null
  if (!user) return false
  if (hasRole(user, 'admin')) return true
  return { id: { equals: user.id } }
}

/**
 * Contributors may save drafts but must not publish. Payload sets `_status`
 * to "published" on publish, so we reject that transition for contributors.
 */
export const preventContributorPublish: CollectionBeforeChangeHook = ({ data, req }) => {
  const user = userOf(req)
  if (!user) return data
  const mayPublish = hasRole(user, 'admin', 'editor', 'admissions')
  if (!mayPublish && data?._status === 'published') {
    throw new Error('Your role can save drafts but cannot publish. Ask an editor to review and publish.')
  }
  return data
}
