import type { CollectionConfig } from 'payload'
import { ROLES, adminOrSelf, hasRole, isAdmin, isAdminField } from '@/access'

export const Users: CollectionConfig = {
  slug: 'users',
  labels: { singular: 'Staff user', plural: 'Staff users' },
  admin: {
    useAsTitle: 'name',
    defaultColumns: ['name', 'email', 'roles'],
    group: 'Administration',
    description: 'People who can sign in to the website editor.',
  },
  auth: {
    maxLoginAttempts: 5,
    lockTime: 15 * 60 * 1000,
    tokenExpiration: 8 * 60 * 60,
    cookies: { sameSite: 'Lax', secure: process.env.NODE_ENV === 'production' },
  },
  access: {
    // Only admins can create accounts; there is no public sign-up.
    create: isAdmin,
    read: adminOrSelf,
    update: adminOrSelf,
    delete: isAdmin,
    admin: ({ req }) => Boolean(req.user),
  },
  fields: [
    { name: 'name', type: 'text', required: true },
    {
      name: 'roles',
      type: 'select',
      hasMany: true,
      required: true,
      saveToJWT: true,
      defaultValue: ['contributor'],
      options: ROLES.map((role) => ({
        value: role,
        label: {
          admin: 'Administrator — everything, including users and settings',
          editor: 'Editor — edit and publish all content',
          contributor: 'Contributor — write drafts for review',
          admissions: 'Admissions — enquiries, events, FAQs and fees',
        }[role],
      })),
      access: { create: isAdminField, update: isAdminField },
    },
  ],
  hooks: {
    beforeChange: [
      // Guarantee at least one administrator always exists.
      async ({ data, req, originalDoc, operation }) => {
        if (operation !== 'update' || !originalDoc || !hasRole(originalDoc, 'admin') || hasRole(data, 'admin')) return data
        if (!data.roles) return data
        const admins = await req.payload.count({ collection: 'users', where: { roles: { contains: 'admin' } }, req })
        if (admins.totalDocs <= 1) throw new Error('The last administrator cannot lose the administrator role.')
        return data
      },
    ],
  },
}
