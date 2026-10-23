import type { CollectionConfig } from 'payload'
import { canEditAdmissionsContent, canManageAdmissions, preventContributorPublish, publishedOrAuthenticated } from '@/access'
import { slugField } from '@/fields/slug'
import { SCHOOL_TIMEZONE } from '@/lib/routes'
import { revalidateAfterChange, revalidateAfterDelete } from '@/hooks/revalidate'


export const Events: CollectionConfig = {
  slug: 'events',
  labels: { singular: 'Event', plural: 'Events' },
  admin: {
    useAsTitle: 'title',
    defaultColumns: ['title', 'startsAt', 'campus', '_status'],
    group: 'Admissions',
    description: 'Open days, information evenings and other public events. Events stop showing on the website automatically once they end.',
  },
  defaultSort: 'startsAt',
  access: {
    read: publishedOrAuthenticated,
    create: canEditAdmissionsContent,
    update: canEditAdmissionsContent,
    delete: canManageAdmissions,
  },
  versions: { drafts: true, maxPerDoc: 20 },
  fields: [
    { name: 'title', type: 'text', required: true },
    {
      type: 'row',
      fields: [
        {
          name: 'startsAt',
          type: 'date',
          required: true,
          timezone: { defaultTimezone: SCHOOL_TIMEZONE, supportedTimezones: [{ label: 'Kuala Lumpur (MYT)', value: SCHOOL_TIMEZONE }] },
          admin: { width: '50%', date: { pickerAppearance: 'dayAndTime', displayFormat: 'd MMM yyyy, h:mm a' } },
        },
        {
          name: 'endsAt',
          type: 'date',
          required: true,
          timezone: { defaultTimezone: SCHOOL_TIMEZONE, supportedTimezones: [{ label: 'Kuala Lumpur (MYT)', value: SCHOOL_TIMEZONE }] },
          admin: { width: '50%', date: { pickerAppearance: 'dayAndTime', displayFormat: 'd MMM yyyy, h:mm a' } },
          validate: (value: unknown, { siblingData }: { siblingData: { startsAt?: string } }) =>
            !value || !siblingData?.startsAt || new Date(value as string) > new Date(siblingData.startsAt)
              ? true
              : 'The end must be after the start.',
        },
      ],
    },
    {
      type: 'row',
      fields: [
        {
          name: 'campus',
          type: 'select',
          required: true,
          options: [
            { label: 'Main Campus', value: 'main' },
            { label: 'Early Years Centre', value: 'eyc' },
            { label: 'Off site', value: 'offsite' },
            { label: 'Online', value: 'online' },
          ],
          admin: { width: '50%' },
        },
        { name: 'audience', type: 'text', admin: { width: '50%', description: 'e.g. "Nursery & Reception (ages 3–5)"' } },
      ],
    },
    { name: 'location', type: 'text' },
    { name: 'summary', type: 'textarea', required: true },
    {
      name: 'registrationUrl',
      type: 'text',
      admin: { description: 'Booking link. Check it points at this year’s event before publishing.' },
    },
    {
      name: 'registrationStatus',
      type: 'select',
      defaultValue: 'open',
      options: [
        { label: 'Registration open', value: 'open' },
        { label: 'Waiting list', value: 'waitlist' },
        { label: 'Fully booked', value: 'full' },
        { label: 'No registration needed', value: 'none' },
      ],
    },
    { name: 'organiser', type: 'text', defaultValue: 'Admissions' },
    slugField('title'),
  ],
  hooks: {
    beforeChange: [preventContributorPublish],
    afterChange: [revalidateAfterChange],
    afterDelete: [revalidateAfterDelete],
  },
}
