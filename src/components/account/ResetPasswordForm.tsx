'use client'

import React, { useActionState } from 'react'

import { resetPasswordAction } from '@/app/(frontend)/actions/account'
import { idle, type ActionState } from '@/app/(frontend)/actions/types'
import { FormStatus, SubmitButton, TextField } from '@/components/forms/fields'

export const ResetPasswordForm = ({ token }: { token: string }) => {
  const [state, action] = useActionState<ActionState, FormData>(resetPasswordAction, idle)
  const e = state.errors ?? {}
  return (
    <form action={action} className="form" noValidate>
      <FormStatus state={state} labels={{ password: 'Password', confirm: 'Confirm password', token: 'Reset link' }} />
      <input type="hidden" name="token" value={token} />
      <TextField name="password" label="New password" type="password" required autoComplete="new-password" hint="At least 10 characters." error={e.password} />
      <TextField name="confirm" label="Confirm new password" type="password" required autoComplete="new-password" error={e.confirm} />
      <SubmitButton className="btn btn--block" pendingLabel="Saving…">
        Save new password
      </SubmitButton>
    </form>
  )
}
