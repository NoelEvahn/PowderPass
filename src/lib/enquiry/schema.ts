import { z } from 'zod'

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

const trimmed = (max: number) => z.string().trim().max(max, `Please keep this under ${max} characters.`)

/** One schema validates in the browser (for instant feedback) and again on the server (authoritative). */
export const enquirySchema = z
  .object({
    formType: z.enum(FORM_TYPES),
    name: trimmed(120).min(2, 'Please enter your name.'),
    email: trimmed(200).min(1, 'Please enter your email address.').pipe(z.email('Please enter a valid email address, like name@example.com.')),
    phone: trimmed(25)
      .refine((v) => v === '' || /^[+()\d\s-]{6,25}$/.test(v), 'Please enter a valid phone number, including the country code if outside Malaysia.')
      .optional()
      .default(''),
    stage: z.union([z.enum(STAGES), z.literal('')]).optional().default(''),
    intendedStart: trimmed(80).optional().default(''),
    message: trimmed(2000).optional().default(''),
    consentPrivacy: z.literal('on', { message: 'Please confirm you have read the privacy notice.' }),
    consentUpdates: z.literal('on').optional(),
  })
  .superRefine((data, ctx) => {
    if (data.formType !== 'contact' && !data.stage) {
      ctx.addIssue({ code: 'custom', path: ['stage'], message: 'Please choose the stage you are interested in.' })
    }
    if (data.formType === 'contact' && data.message.length < 10) {
      ctx.addIssue({ code: 'custom', path: ['message'], message: 'Please tell us how we can help (at least 10 characters).' })
    }
  })

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
