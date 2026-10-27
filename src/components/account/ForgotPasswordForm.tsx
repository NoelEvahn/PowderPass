'use client'

import React, { useActionState } from 'react'

import { forgotPasswordAction } from '@/app/(frontend)/actions/account'
import { idle, type ActionState } from '@/app/(frontend)/actions/types'
import { FormStatus, SubmitButton, TextField } from '@/components/forms/fields'

export const ForgotPasswordForm = () => {
  const [state, action] = useActionState<ActionState, FormData>(forgotPasswordAction, idle)
  return (
    <form action={action} className="form" noValidate>
      <FormStatus state={state} labels={{ email: 'Email' }} />
      <TextField name="email" label="Email" type="email" required autoComplete="email" defaultValue={state.values?.email} error={state.errors?.email} />
      <SubmitButton className="btn btn--block" pendingLabel="Sending…">
        Send reset link
      </SubmitButton>
    </form>
  )
}
