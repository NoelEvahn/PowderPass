import type { Access, FieldAccess, PayloadRequest, Where } from 'payload'

export type StaffRole = 'admin' | 'editor' | 'sales'

type AnyUser = PayloadRequest['user']

export const isStaffUser = (user: AnyUser): boolean => Boolean(user && user.collection === 'users')

export const isCustomerUser = (user: AnyUser): boolean =>
  Boolean(user && user.collection === 'customers')

export const userHasRole = (user: AnyUser, ...roles: StaffRole[]): boolean => {
  if (!user || user.collection !== 'users') return false
  const userRoles = ((user as { roles?: StaffRole[] | null }).roles ?? []) as StaffRole[]
  if (userRoles.includes('admin')) return true
  return roles.some((role) => userRoles.includes(role))
}

export const anyone: Access = () => true
export const nobody: Access = () => false

export const staff: Access = ({ req: { user } }) => isStaffUser(user)
export const admins: Access = ({ req: { user } }) => userHasRole(user, 'admin')
export const editors: Access = ({ req: { user } }) => userHasRole(user, 'editor')
export const sales: Access = ({ req: { user } }) => userHasRole(user, 'sales')
export const editorsOrSales: Access = ({ req: { user } }) => userHasRole(user, 'editor', 'sales')

/** Staff see drafts; everyone else only sees published documents. */
export const publishedOrStaff: Access = ({ req: { user } }) => {
  if (isStaffUser(user)) return true
  return { _status: { equals: 'published' } } as Where
}

export const adminFieldOnly: FieldAccess = ({ req: { user } }) => userHasRole(user, 'admin')
export const staffFieldOnly: FieldAccess = ({ req: { user } }) => isStaffUser(user)
