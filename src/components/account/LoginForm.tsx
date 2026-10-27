'use client'

import Link from 'next/link'
import React, { useActionState } from 'react'

import { loginAction } from '@/app/(frontend)/actions/account'
import { idle, type ActionState } from '@/app/(frontend)/actions/types'
import { FormStatus, SubmitButton, TextField } from '@/components/forms/fields'

export const LoginForm = ({ next }: { next: string }) => {
  const [state, action] = useActionState<ActionState, FormData>(loginAction, idle)
  return (
    <form action={action} className="form" noValidate>
      <FormStatus state={state} labels={{ email: 'Email', password: 'Password' }} />
      <input type="hidden" name="next" value={next} />
      <TextField name="email" label="Email" type="email" required autoComplete="email" defaultValue={state.values?.email} error={state.errors?.email} />
      <TextField name="password" label="Password" type="password" required autoComplete="current-password" error={state.errors?.password} />
      <SubmitButton className="btn btn--block" pendingLabel="Signing in…">
        Sign in
      </SubmitButton>
      <p className="small">
        <Link href="/account/forgot-password">Forgot your password?</Link>
      </p>
    </form>
  )
}
