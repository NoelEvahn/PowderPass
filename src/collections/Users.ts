import { APIError, type CollectionConfig } from 'payload'

import { admins, isStaffUser, userHasRole, adminFieldOnly } from '@/access'

/** Staff accounts. There is no public sign-up: only an administrator can add staff. */
export const Users: CollectionConfig = {
  slug: 'users',
  labels: { singular: 'Staff member', plural: 'Staff' },
  admin: {
    useAsTitle: 'name',
    defaultColumns: ['name', 'email', 'roles'],
    group: 'Settings',
    description:
      'People who can sign in to this editor. Administrators manage staff and shop settings; editors manage stock and pages; sales manage orders and enquiries.',
  },
  auth: {
    tokenExpiration: 60 * 60 * 8,
    maxLoginAttempts: 5,
    lockTime: 15 * 60 * 1000,
    cookies: { sameSite: 'Lax', secure: process.env.NODE_ENV === 'production' },
  },
  access: {
    admin: ({ req: { user } }) => isStaffUser(user),
    create: admins,
    delete: admins,
    read: ({ req: { user } }) => {
      if (userHasRole(user, 'admin')) return true
      if (isStaffUser(user)) return { id: { equals: user!.id } }
      return false
    },
    update: ({ req: { user } }) => {
      if (userHasRole(user, 'admin')) return true
      if (isStaffUser(user)) return { id: { equals: user!.id } }
      return false
    },
  },
  fields: [
    { name: 'name', type: 'text', required: true },
    {
      name: 'roles',
      type: 'select',
      hasMany: true,
      required: true,
      defaultValue: ['editor'],
      saveToJWT: true,
      access: { create: adminFieldOnly, update: adminFieldOnly },
      options: [
        { label: 'Administrator (everything)', value: 'admin' },
        { label: 'Editor (stock, pages, images)', value: 'editor' },
        { label: 'Sales (orders, enquiries, stock status)', value: 'sales' },
      ],
    },
  ],
  hooks: {
    beforeChange: [
      async ({ req, data, originalDoc, operation }) => {
        if (operation !== 'update' || !originalDoc) return data
        const wasAdmin = (originalDoc.roles ?? []).includes('admin')
        const staysAdmin = (data.roles ?? originalDoc.roles ?? []).includes('admin')
        if (wasAdmin && !staysAdmin) {
          const { totalDocs } = await req.payload.count({
            collection: 'users',
            where: { roles: { contains: 'admin' } },
            req,
          })
          if (totalDocs <= 1) throw new APIError('The last administrator cannot be demoted.', 400, null, true)
        }
        return data
      },
    ],
    beforeDelete: [
      async ({ req, id }) => {
        const doc = await req.payload.findByID({ collection: 'users', id, depth: 0, req })
        if ((doc.roles ?? []).includes('admin')) {
          const { totalDocs } = await req.payload.count({
            collection: 'users',
            where: { roles: { contains: 'admin' } },
            req,
          })
          if (totalDocs <= 1) throw new APIError('The last administrator cannot be deleted.', 400, null, true)
        }
      },
    ],
  },
}
