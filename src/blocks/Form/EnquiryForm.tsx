'use client'
import { AlertCircle, CheckCircle2, Loader2 } from 'lucide-react'
import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { useActionState, useEffect, useId, useRef, useState } from 'react'
import { trackEvent } from '@/components/consent/Analytics'
import { submitEnquiry } from '@/lib/enquiry/action'
import { type EnquiryState, type FieldName, type FormType, STAGE_LABELS, STAGES, validate } from '@/lib/enquiry/schema'
import styles from './Form.module.css'

type Props = { formType: FormType; startOptions: string[]; fallbackEmail: string }

const SUBMIT_LABEL: Record<FormType, string> = {
  enquiry: 'Send enquiry',
  visit: 'Request a visit',
  'open-day': 'Register interest',
  contact: 'Send message',
}

const FIELD_ORDER: FieldName[] = ['name', 'email', 'phone', 'stage', 'intendedStart', 'message', 'consentPrivacy']

/**
 * Persistent labels, inline validation after a field is left, an error summary
 * on submit, preserved values after failure, and a pending state that blocks
 * duplicate submissions. Works without JavaScript via the server action.
 */
export function EnquiryForm({ formType, startOptions, fallbackEmail }: Props) {
  const [state, action, pending] = useActionState<EnquiryState, FormData>(submitEnquiry, { status: 'idle' })
  const pathname = usePathname()
  const uid = useId()
  const formRef = useRef<HTMLFormElement>(null)
  const summaryRef = useRef<HTMLDivElement>(null)
  const successRef = useRef<HTMLDivElement>(null)
  const [clientErrors, setClientErrors] = useState<Partial<Record<FieldName, string>>>({})
  const [touched, setTouched] = useState<Set<FieldName>>(new Set())
  const [showSummary, setShowSummary] = useState(false)
  const [startedAt, setStartedAt] = useState(0)
  const startedTracking = useRef(false)

  useEffect(() => setStartedAt(Date.now()), [])

  const serverErrors = state.status === 'invalid' ? state.errors : {}
  const errors: Partial<Record<FieldName, string>> = { ...serverErrors, ...clientErrors }
  const values = state.status === 'invalid' || state.status === 'error' ? state.values : {}

  useEffect(() => {
    if (state.status === 'invalid' || state.status === 'error') {
      setShowSummary(true)
      requestAnimationFrame(() => summaryRef.current?.focus())
    }
    if (state.status === 'success') {
      successRef.current?.focus()
      trackEvent('enquiry_submitted', { form_type: formType })
    }
  }, [state, formType])

  const currentValues = () => {
    const form = formRef.current
    if (!form) return {}
    const data = new FormData(form)
    return Object.fromEntries(['formType', ...FIELD_ORDER].map((k) => [k, (data.get(k) as string | null) ?? undefined]))
  }

  const revalidateField = (name: FieldName) => {
    const { errors: next } = validate(currentValues())
    setClientErrors((prev) => {
      const copy = { ...prev }
      if (next?.[name]) copy[name] = next[name]
      else delete copy[name]
      return copy
    })
  }

  const onBlur = (name: FieldName) => {
    setTouched((prev) => new Set(prev).add(name))
    revalidateField(name)
  }

  const onChange = (name: FieldName) => {
    if (!startedTracking.current) {
      startedTracking.current = true
      trackEvent('enquiry_started', { form_type: formType })
    }
    if (touched.has(name) || errors[name]) revalidateField(name)
  }

  const onSubmit = (event: React.FormEvent<HTMLFormElement>) => {
    const { errors: next } = validate(currentValues())
    if (next) {
      event.preventDefault()
      setClientErrors(next)
      setTouched(new Set(FIELD_ORDER))
      setShowSummary(true)
      requestAnimationFrame(() => summaryRef.current?.focus())
    } else {
      setClientErrors({})
    }
  }

  if (state.status === 'success') {
    return (
      <div ref={successRef} tabIndex={-1} className={styles.success} role="status">
        <CheckCircle2 size={36} aria-hidden="true" className={styles.successIcon} />
        <h3 className={styles.successTitle}>Thank you, {state.firstName}. Your enquiry has reached our Admissions team.</h3>
        <p>
          Your reference is <strong>{state.reference}</strong>. A member of the team will reply by email. Nothing further is needed from you for now.
        </p>
        <p className={styles.small}>
          If your question is urgent, email <a href={`mailto:${fallbackEmail}`}>{fallbackEmail}</a>.
        </p>
      </div>
    )
  }

  const id = (name: string) => `${uid}-${name}`
  const errorIds = FIELD_ORDER.filter((name) => errors[name])
  const fieldProps = (name: FieldName) => ({
    id: id(name),
    name,
    'aria-invalid': errors[name] ? true : undefined,
    'aria-describedby': [errors[name] ? id(`${name}-error`) : '', id(`${name}-hint`)].filter(Boolean).join(' ') || undefined,
    onBlur: () => onBlur(name),
    onChange: () => onChange(name),
    defaultValue: values[name],
  })
  const ErrorText = ({ name }: { name: FieldName }) =>
    errors[name] ? (
      <p id={id(`${name}-error`)} className={styles.error}>
        <AlertCircle size={16} aria-hidden="true" /> {errors[name]}
      </p>
    ) : null

  const needsStage = formType !== 'contact'
  const labels: Record<FieldName, string> = {
    name: 'Your name (parent or carer)',
    email: 'Email address',
    phone: 'Phone number',
    stage: 'Stage of interest',
    intendedStart: 'When would your child join?',
    message: formType === 'contact' ? 'How can we help?' : 'Anything you would like us to know',
    consentPrivacy: 'Privacy notice',
  }

  return (
    <form ref={formRef} action={action} onSubmit={onSubmit} noValidate className={styles.form} aria-describedby={id('intro')}>
      <p id={id('intro')} className={styles.small}>
        All fields are required unless marked optional. Please do not include medical details, reports or passport information here.
      </p>

      {showSummary && (errorIds.length > 0 || state.status === 'error') && (
        <div ref={summaryRef} tabIndex={-1} className={styles.summary} role="alert">
          {state.status === 'error' && errorIds.length === 0 ? (
            <p>{state.message}</p>
          ) : (
            <>
              <p className={styles.summaryTitle}>Please check {errorIds.length === 1 ? 'one answer' : `${errorIds.length} answers`}:</p>
              <ul>
                {errorIds.map((name) => (
                  <li key={name}>
                    <a
                      href={`#${id(name)}`}
                      onClick={(e) => {
                        e.preventDefault()
                        document.getElementById(id(name))?.focus()
                      }}
                    >
                      {labels[name]}: {errors[name]}
                    </a>
                  </li>
                ))}
              </ul>
            </>
          )}
        </div>
      )}

      <input type="hidden" name="formType" value={formType} />
      <input type="hidden" name="sourcePath" value={pathname} />
      <input type="hidden" name="startedAt" value={startedAt} />
      {/* Honeypot: hidden from people and assistive tech; bots tend to fill it. */}
      <div className={styles.honeypot} aria-hidden="true">
        <label htmlFor={id('company_website')}>Leave this empty</label>
        <input id={id('company_website')} name="company_website" type="text" tabIndex={-1} autoComplete="off" />
      </div>

      <div className={styles.row}>
        <div className={styles.field}>
          <label htmlFor={id('name')}>{labels.name}</label>
          <input type="text" autoComplete="name" {...fieldProps('name')} />
          <ErrorText name="name" />
        </div>
        <div className={styles.field}>
          <label htmlFor={id('email')}>{labels.email}</label>
          <input type="email" autoComplete="email" inputMode="email" {...fieldProps('email')} />
          <ErrorText name="email" />
        </div>
      </div>

      <div className={styles.row}>
        <div className={styles.field}>
          <label htmlFor={id('phone')}>
            {labels.phone} <span className={styles.optional}>(optional)</span>
          </label>
          <input type="tel" autoComplete="tel" inputMode="tel" {...fieldProps('phone')} />
          <p id={id('phone-hint')} className={styles.hint}>
            Include the country code if outside Malaysia.
          </p>
          <ErrorText name="phone" />
        </div>
        {needsStage && (
          <div className={styles.field}>
            <label htmlFor={id('stage')}>{labels.stage}</label>
            <select {...fieldProps('stage')} defaultValue={values.stage ?? ''}>
              <option value="">Choose a stage</option>
              {STAGES.map((s) => (
                <option key={s} value={s}>
                  {STAGE_LABELS[s]}
                </option>
              ))}
            </select>
            <ErrorText name="stage" />
          </div>
        )}
      </div>

      {needsStage && (
        <div className={styles.field}>
          <label htmlFor={id('intendedStart')}>
            {labels.intendedStart} <span className={styles.optional}>(optional)</span>
          </label>
          <select {...fieldProps('intendedStart')} defaultValue={values.intendedStart ?? ''}>
            <option value="">Choose a timeframe</option>
            {startOptions.map((option) => (
              <option key={option} value={option}>
                {option}
              </option>
            ))}
          </select>
          <ErrorText name="intendedStart" />
        </div>
      )}

      <div className={styles.field}>
        <label htmlFor={id('message')}>
          {labels.message} {formType !== 'contact' && <span className={styles.optional}>(optional)</span>}
        </label>
        <textarea rows={5} maxLength={2000} {...fieldProps('message')} />
        <p id={id('message-hint')} className={styles.hint}>
          {formType === 'visit' ? 'Tell us which days or times usually suit you for a tour.' : 'Up to 2,000 characters.'}
        </p>
        <ErrorText name="message" />
      </div>

      <div className={styles.checks}>
        <div className={styles.check}>
          <input
            type="checkbox"
            id={id('consentPrivacy')}
            name="consentPrivacy"
            aria-invalid={errors.consentPrivacy ? true : undefined}
            aria-describedby={errors.consentPrivacy ? id('consentPrivacy-error') : undefined}
            defaultChecked={values.consentPrivacy === 'on'}
            onChange={() => onChange('consentPrivacy')}
            onBlur={() => onBlur('consentPrivacy')}
          />
          <label htmlFor={id('consentPrivacy')}>
            I have read the <Link href="/privacy">privacy notice</Link> and agree that ISP may use these details to respond to my enquiry.
          </label>
        </div>
        <ErrorText name="consentPrivacy" />
        <div className={styles.check}>
          <input type="checkbox" id={id('consentUpdates')} name="consentUpdates" defaultChecked={values.consentUpdates === 'on'} />
          <label htmlFor={id('consentUpdates')}>
            Optional: send me occasional emails about open days and admissions events. You can unsubscribe at any time.
          </label>
        </div>
      </div>

      <div className={styles.submitRow}>
        <button type="submit" className={styles.submit} disabled={pending} aria-disabled={pending}>
          {pending && <Loader2 size={20} aria-hidden="true" className={styles.spinner} />}
          {pending ? 'Sending…' : SUBMIT_LABEL[formType]}
        </button>
        <p className={styles.small} aria-live="polite">
          {pending ? 'Sending your enquiry securely…' : ''}
        </p>
      </div>
    </form>
  )
}
