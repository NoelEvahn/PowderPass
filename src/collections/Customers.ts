import type { CollectionConfig } from 'payload'

import { admins, isCustomerUser, isStaffUser, userHasRole } from '@/access'
import { getServerURL } from '@/lib/env'
import { emailLayout, escapeHtml } from '@/lib/email-templates'

/**
 * Shop customers. Sign-up, sign-in and password reset run through the site's own
 * forms (rate-limited server actions), so the public REST create endpoint is closed.
 */
export const Customers: CollectionConfig = {
  slug: 'customers',
  labels: { singular: 'Customer', plural: 'Customers' },
  admin: {
    useAsTitle: 'email',
    defaultColumns: ['name', 'email', 'phone', 'createdAt'],
    group: 'Shop',
  },
  auth: {
    tokenExpiration: 60 * 60 * 24 * 14,
    maxLoginAttempts: 5,
    lockTime: 15 * 60 * 1000,
    cookies: { sameSite: 'Lax', secure: process.env.NODE_ENV === 'production' },
    forgotPassword: {
      expiration: 60 * 60 * 1000,
      generateEmailSubject: () => 'Reset your Label Trunc password',
      generateEmailHTML: (args) => {
        const token = args?.token ?? ''
        const url = `${getServerURL()}/account/reset-password?token=${encodeURIComponent(token)}`
        return emailLayout(
          'Reset your password',
          `<p>Someone asked to reset the password for this Label Trunc account.</p>
           <p><a href="${escapeHtml(url)}">Choose a new password</a> (this link works for one hour).</p>
           <p>If this wasn't you, you can ignore this email; your password stays the same.</p>`,
        )
      },
    },
  },
  access: {
    admin: () => false,
    create: ({ req: { user } }) => userHasRole(user, 'admin'),
    delete: admins,
    read: ({ req: { user } }) => {
      if (userHasRole(user, 'sales')) return true
      if (isCustomerUser(user)) return { id: { equals: user!.id } }
      return false
    },
    update: ({ req: { user } }) => {
      if (userHasRole(user, 'sales')) return true
      if (isCustomerUser(user)) return { id: { equals: user!.id } }
      return false
    },
  },
  fields: [
    { name: 'name', type: 'text', required: true, maxLength: 120 },
    { name: 'phone', type: 'text', maxLength: 40, label: 'Phone / WhatsApp' },
    {
      name: 'marketingOptIn',
      type: 'checkbox',
      label: 'Wants to hear about new arrivals',
      defaultValue: false,
    },
    {
      name: 'staffNotes',
      type: 'textarea',
      access: {
        read: ({ req: { user } }) => isStaffUser(user),
        update: ({ req: { user } }) => isStaffUser(user),
        create: ({ req: { user } }) => isStaffUser(user),
      },
      admin: { description: 'Only staff can see this.' },
    },
  ],
}
