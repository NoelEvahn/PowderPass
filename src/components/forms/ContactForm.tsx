'use client'

import Link from 'next/link'
import React, { useActionState, useState } from 'react'

import { enquiryAction } from '@/app/(frontend)/actions/contact'
import { idle } from '@/app/(frontend)/actions/types'
import { FormStatus, SelectField, SubmitButton, TextAreaField, TextField } from '@/components/forms/fields'

type Topic = { label: string; value: string }

type Props = {
  /** Server render time, for the minimum fill-time spam check. */
  startedAt: number
  topics: readonly Topic[]
  defaultTopic?: string
  productId?: number
  productTitle?: string
}

const LABELS: Record<string, string> = {
  name: 'Name',
  email: 'Email',
  phone: 'Phone',
  topic: 'Topic',
  message: 'Message',
  consent: 'Consent',
}

export const ContactForm = ({ topics, defaultTopic, productId, productTitle, startedAt }: Props) => {
  const [state, formAction] = useActionState(enquiryAction, idle)
  // Set after mount so the server and first client render match; the action treats a missing value as fine.
  const [consent, setConsent] = useState(false)
  if (state.status === 'success') {
    return (
      <div className="stack" style={{ ['--stack-gap' as string]: 'var(--s-5)' }}>
        <FormStatus state={state} />
        <div className="cluster">
          <Link href="/shop" className="btn">
            Browse the shop
          </Link>
          <Link href="/" className="btn btn--secondary">
            Back to the home page
          </Link>
        </div>
      </div>
    )
  }

  const v = state.values ?? {}
  const e = state.errors ?? {}
  return (
    <form action={formAction} className="form" noValidate={false}>
      <FormStatus state={state} labels={LABELS} />
      {productTitle ? (
        <p className="contact-about">
          <span className="meta">About</span> <strong>{productTitle}</strong>
        </p>
      ) : null}
      {productId ? <input type="hidden" name="product" value={productId} /> : null}
      <input type="hidden" name="startedAt" value={String(startedAt)} />

      <TextField name="name" label="Name" required autoComplete="name" maxLength={120} defaultValue={v.name} error={e.name} />

      <fieldset className="field-group">
        <legend>How can we reach you?</legend>
        <p className="hint">Email or phone, so we can reply. One is enough.</p>
        <div className="form-row form-row--2">
          <TextField
            name="email"
            type="email"
            label="Email"
            autoComplete="email"
            maxLength={200}
            defaultValue={v.email}
            error={e.email}
          />
          <TextField
            name="phone"
            type="tel"
            label="Phone"
            autoComplete="tel"
            inputMode="tel"
            maxLength={40}
            defaultValue={v.phone}
            error={e.phone}
          />
        </div>
      </fieldset>

      <SelectField
        name="topic"
        label="What is this about?"
        required
        options={topics}
        placeholder="Choose a topic"
        defaultValue={v.topic ?? defaultTopic}
        error={e.topic}
      />
      <TextAreaField name="message" label="Message" required rows={6} maxLength={4000} defaultValue={v.message} error={e.message} />

      <div className="field">
        <label className="checkbox" htmlFor="consent">
          <input
            id="consent"
            name="consent"
            type="checkbox"
            required
            checked={consent}
            onChange={(ev) => setConsent(ev.target.checked)}
            aria-invalid={e.consent ? true : undefined}
            aria-describedby={e.consent ? 'consent-error' : undefined}
          />
          <span>
            I agree that Label Trunc will use these details to reply to my enquiry, as described in the{' '}
            <Link href="/privacy">privacy notice</Link>.
          </span>
        </label>
        {e.consent ? (
          <p id="consent-error" className="field-error">
            {e.consent}
          </p>
        ) : null}
      </div>

      <div className="hp-field" aria-hidden="true">
        <label htmlFor="website">Leave this field empty</label>
        <input id="website" name="website" type="text" tabIndex={-1} autoComplete="off" defaultValue="" />
      </div>

      <div>
        <SubmitButton pendingLabel="Sending…">Send message</SubmitButton>
      </div>
    </form>
  )
}
