import * as z from 'zod/mini'

export const FORM_TYPES = ['enquiry', 'visit', 'open-day', 'contact'] as const
export const STAGES = ['early-years', 'primary', 'secondary', 'sixth-form', 'unsure'] as const
export type FormType = (typeof FORM_TYPES)[number]

export const STAGE_LABELS: Record<(typeof STAGES)[number], string> = {
  'early-years': 'Early Years (ages 3–5)',
  primary: 'Primary (ages 5–11)',
  secondary: 'Secondary (ages 11–16)',
  'sixth-form': 'Sixth Form (ages 16–18)',
  unsure: 'Not sure yet',
}

/** Start options relative to the current academic year (which begins in August). */
export function intendedStartOptions(now = new Date()): string[] {
  const y = now.getMonth() >= 7 ? now.getFullYear() : now.getFullYear() - 1
  const label = (start: number) => `${start}/${String(start + 1).slice(-2)}`
  return [`As soon as possible (${label(y)})`, `Academic year ${label(y + 1)}`, `Academic year ${label(y + 2)}`, 'Later / not sure yet']
}

const MAX = (n: number) => z.maxLength(n, `Please keep this under ${n} characters.`)
const optionalText = (max: number) => z._default(z.optional(z.string().check(z.trim(), MAX(max))), '')

/**
 * One schema validates in the browser (for instant feedback) and again on the
 * server (authoritative). zod/mini keeps the browser bundle small.
 */
export const enquirySchema = z
  .object({
    formType: z.enum(FORM_TYPES),
    name: z.string().check(z.trim(), MAX(120), z.minLength(2, 'Please enter your name.')),
    email: z
      .string()
      .check(z.trim(), MAX(200), z.minLength(1, 'Please enter your email address.'), z.email('Please enter a valid email address, like name@example.com.')),
    phone: z._default(
      z.optional(
        z
          .string()
          .check(
            z.trim(),
            MAX(25),
            z.refine((v) => v === '' || /^[+()\d\s-]{6,25}$/.test(v), 'Please enter a valid phone number, including the country code if outside Malaysia.'),
          ),
      ),
      '',
    ),
    stage: z._default(z.optional(z.union([z.enum(STAGES), z.literal('')])), ''),
    intendedStart: optionalText(80),
    message: optionalText(2000),
    consentPrivacy: z.literal('on', 'Please confirm you have read the privacy notice.'),
    consentUpdates: z.optional(z.literal('on')),
  })
  .check(
    z.superRefine((data, ctx) => {
      if (data.formType !== 'contact' && !data.stage) {
        ctx.issues.push({ code: 'custom', path: ['stage'], message: 'Please choose the stage you are interested in.', input: data.stage })
      }
      if (data.formType === 'contact' && data.message.length < 10) {
        ctx.issues.push({ code: 'custom', path: ['message'], message: 'Please tell us how we can help (at least 10 characters).', input: data.message })
      }
    }),
  )

export type EnquiryInput = z.infer<typeof enquirySchema>
export type FieldName = 'name' | 'email' | 'phone' | 'stage' | 'intendedStart' | 'message' | 'consentPrivacy'

export type EnquiryState =
  | { status: 'idle' }
  | { status: 'invalid'; errors: Partial<Record<FieldName, string>>; values: Record<string, string> }
  | { status: 'error'; message: string; values: Record<string, string> }
  | { status: 'success'; reference: string; firstName: string }

export function validate(values: Record<string, unknown>) {
  const result = enquirySchema.safeParse(values)
  if (result.success) return { data: result.data, errors: null }
  const errors: Partial<Record<FieldName, string>> = {}
  for (const issue of result.error.issues) {
    const key = issue.path[0] as FieldName
    if (key && !errors[key]) errors[key] = issue.message
  }
  return { data: null, errors }
}
