'use client'

import React, { useActionState } from 'react'

import { updateProfileAction } from '@/app/(frontend)/actions/account'
import { idle, type ActionState } from '@/app/(frontend)/actions/types'
import { FormStatus, SubmitButton, TextField } from '@/components/forms/fields'

type Props = { name: string; email: string; phone: string; marketingOptIn: boolean }

export const ProfileForm = ({ name, email, phone, marketingOptIn }: Props) => {
  // React resets the form after an action, so keep what was saved as the new defaults.
  const [state, action] = useActionState<ActionState, FormData>(async (prev, form) => {
    const result = await updateProfileAction(prev, form)
    if (result.status !== 'success') return result
    return {
      ...result,
      values: {
        name: String(form.get('name') ?? ''),
        phone: String(form.get('phone') ?? ''),
        marketingOptIn: form.get('marketingOptIn') ? 'on' : '',
      },
    }
  }, idle)
  const v = state.values
  const e = state.errors ?? {}
  return (
    <form action={action} className="form" noValidate>
      <FormStatus state={state} labels={{ name: 'Name', phone: 'Phone' }} />
      <TextField name="name" label="Full name" required autoComplete="name" defaultValue={v?.name ?? name} error={e.name} />
      <div className="field">
        <label htmlFor="profile-email">Email</label>
        <input id="profile-email" className="input" type="email" value={email} readOnly aria-describedby="profile-email-hint" />
        <p id="profile-email-hint" className="hint">
          To change your email, message us.
        </p>
      </div>
      <TextField name="phone" label="Phone / WhatsApp" type="tel" inputMode="tel" autoComplete="tel" defaultValue={v?.phone ?? phone} error={e.phone} />
      <label className="checkbox">
        <input type="checkbox" name="marketingOptIn" defaultChecked={v ? v.marketingOptIn === 'on' : marketingOptIn} />
        <span>Tell me when new pieces arrive.</span>
      </label>
      <div>
        <SubmitButton pendingLabel="Saving…">Save details</SubmitButton>
      </div>
    </form>
  )
}
