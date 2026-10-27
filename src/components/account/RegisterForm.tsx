'use client'

import React, { useActionState } from 'react'

import { registerAction } from '@/app/(frontend)/actions/account'
import { idle, type ActionState } from '@/app/(frontend)/actions/types'
import { FormStatus, SubmitButton, TextField } from '@/components/forms/fields'

export const RegisterForm = ({ next }: { next: string }) => {
  const [state, action] = useActionState<ActionState, FormData>(registerAction, idle)
  const v = state.values ?? {}
  const e = state.errors ?? {}
  return (
    <form action={action} className="form" noValidate>
      <FormStatus state={state} labels={{ name: 'Name', email: 'Email', phone: 'Phone', password: 'Password' }} />
      <input type="hidden" name="next" value={next} />
      <div className="hp-field" aria-hidden="true">
        <label htmlFor="website">Leave this field empty</label>
        <input id="website" name="website" type="text" tabIndex={-1} autoComplete="off" />
      </div>
      <TextField name="name" label="Full name" required autoComplete="name" defaultValue={v.name} error={e.name} />
      <TextField name="email" label="Email" type="email" required autoComplete="email" defaultValue={v.email} error={e.email} />
      <TextField name="phone" label="Phone / WhatsApp" type="tel" inputMode="tel" autoComplete="tel" defaultValue={v.phone} error={e.phone} />
      <TextField
        name="password"
        label="Password"
        type="password"
        required
        autoComplete="new-password"
        hint="At least 10 characters."
        error={e.password}
      />
      <label className="checkbox">
        <input type="checkbox" name="marketingOptIn" />
        <span>Tell me when new pieces arrive. Occasional emails, and you can stop them any time.</span>
      </label>
      <SubmitButton className="btn btn--block" pendingLabel="Creating your account…">
        Create account
      </SubmitButton>
    </form>
  )
}
