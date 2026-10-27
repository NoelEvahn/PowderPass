import type { FieldErrors } from '@/lib/validation'

/** Shape every form action returns, for useActionState. */
export type ActionState = {
  status: 'idle' | 'success' | 'error'
  message?: string
  errors?: FieldErrors
  /** Submitted values, so a form keeps the answers after an error (never passwords). */
  values?: Record<string, string>
}

export const idle: ActionState = { status: 'idle' }
