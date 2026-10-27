'use client'

import React, { useEffect, useRef } from 'react'
import { useFormStatus } from 'react-dom'

import type { ActionState } from '@/app/(frontend)/actions/types'

/**
 * Accessible form primitives: real labels, hints and errors tied to inputs with
 * aria-describedby, an error summary that takes focus, and a pending submit button.
 */

type BaseProps = {
  name: string
  label: string
  hint?: string
  error?: string
  required?: boolean
  className?: string
}

const describedBy = (name: string, hint?: string, error?: string) =>
  [hint ? `${name}-hint` : '', error ? `${name}-error` : ''].filter(Boolean).join(' ') || undefined

const FieldShell = ({
  name,
  label,
  hint,
  error,
  required,
  className,
  children,
}: BaseProps & { children: React.ReactNode }) => (
  <div className={['field', className].filter(Boolean).join(' ')}>
    <label htmlFor={name}>
      {label}
      {required ? null : <span className="muted" style={{ fontWeight: 400 }}> (optional)</span>}
    </label>
    {hint ? (
      <p id={`${name}-hint`} className="hint">
        {hint}
      </p>
    ) : null}
    {children}
    {error ? (
      <p id={`${name}-error`} className="field-error">
        {error}
      </p>
    ) : null}
  </div>
)

export const TextField = ({
  type = 'text',
  defaultValue,
  autoComplete,
  inputMode,
  maxLength,
  ...props
}: BaseProps & {
  type?: 'text' | 'email' | 'tel' | 'password' | 'number' | 'search'
  defaultValue?: string
  autoComplete?: string
  inputMode?: React.HTMLAttributes<HTMLInputElement>['inputMode']
  maxLength?: number
}) => (
  <FieldShell {...props}>
    <input
      id={props.name}
      name={props.name}
      type={type}
      className="input"
      defaultValue={defaultValue}
      autoComplete={autoComplete}
      inputMode={inputMode}
      maxLength={maxLength}
      required={props.required}
      aria-invalid={props.error ? true : undefined}
      aria-describedby={describedBy(props.name, props.hint, props.error)}
    />
  </FieldShell>
)

export const TextAreaField = ({
  defaultValue,
  maxLength,
  rows = 5,
  ...props
}: BaseProps & { defaultValue?: string; maxLength?: number; rows?: number }) => (
  <FieldShell {...props}>
    <textarea
      id={props.name}
      name={props.name}
      className="textarea"
      rows={rows}
      defaultValue={defaultValue}
      maxLength={maxLength}
      required={props.required}
      aria-invalid={props.error ? true : undefined}
      aria-describedby={describedBy(props.name, props.hint, props.error)}
    />
  </FieldShell>
)

export const SelectField = ({
  options,
  defaultValue,
  placeholder,
  ...props
}: BaseProps & { options: readonly { label: string; value: string }[]; defaultValue?: string; placeholder?: string }) => (
  <FieldShell {...props}>
    <select
      id={props.name}
      name={props.name}
      className="select"
      defaultValue={defaultValue ?? ''}
      required={props.required}
      aria-invalid={props.error ? true : undefined}
      aria-describedby={describedBy(props.name, props.hint, props.error)}
    >
      {placeholder !== undefined ? <option value="">{placeholder}</option> : null}
      {options.map((o) => (
        <option key={o.value} value={o.value}>
          {o.label}
        </option>
      ))}
    </select>
  </FieldShell>
)

export const SubmitButton = ({
  children,
  pendingLabel = 'Please wait…',
  className = 'btn',
}: {
  children: React.ReactNode
  pendingLabel?: string
  className?: string
}) => {
  const { pending } = useFormStatus()
  return (
    <button type="submit" className={className} aria-disabled={pending || undefined} disabled={pending}>
      {pending ? pendingLabel : children}
    </button>
  )
}

const FIELD_LABELS: Record<string, string> = {}

/**
 * Shows the result of a form action. On error with field errors it lists them as
 * links to each field and moves focus here so screen-reader users hear it.
 */
export const FormStatus = ({ state, labels = FIELD_LABELS }: { state: ActionState; labels?: Record<string, string> }) => {
  const ref = useRef<HTMLDivElement>(null)
  useEffect(() => {
    if (state.status !== 'idle') ref.current?.focus()
  }, [state])

  if (state.status === 'idle') return null
  const errors = Object.entries(state.errors ?? {})
  if (state.status === 'error') {
    return (
      <div ref={ref} tabIndex={-1} className="error-summary" role="alert">
        <strong>{state.message ?? 'Something went wrong.'}</strong>
        {errors.length ? (
          <ul>
            {errors.map(([field, msg]) => (
              <li key={field}>
                <a href={`#${field}`}>{labels[field] ? `${labels[field]}: ${msg}` : msg}</a>
              </li>
            ))}
          </ul>
        ) : null}
      </div>
    )
  }
  return (
    <div ref={ref} tabIndex={-1} className="notice notice--success" role="status">
      <span>{state.message}</span>
    </div>
  )
}
