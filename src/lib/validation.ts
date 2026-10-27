import { z } from 'zod'

/** Shared validation for every public form. Messages are written for customers. */

const trimmed = (max: number) => z.string().trim().max(max, `Please keep this under ${max} characters.`)

export const phoneSchema = z
  .string()
  .trim()
  .min(1, 'Enter a phone number so we can reach you.')
  .max(40)
  .refine((v) => v.replace(/\D/g, '').length >= 8, 'Enter a phone number with at least 8 digits.')

export const emailSchema = z.string().trim().toLowerCase().min(1, 'Enter your email address.').max(254).email('Enter a valid email address, like name@example.com.')

export const nameSchema = trimmed(120).min(1, 'Enter your name.')

export const passwordSchema = z
  .string()
  .min(10, 'Use at least 10 characters.')
  .max(128, 'Use at most 128 characters.')

export const checkoutSchema = z
  .object({
    name: nameSchema,
    email: emailSchema,
    phone: phoneSchema,
    paymentMethod: z.enum(['online', 'pay_at_shop'], { message: 'Choose how you would like to pay.' }),
    fulfilment: z.enum(['pickup', 'delivery'], { message: 'Choose collection or delivery.' }),
    line1: trimmed(160).optional(),
    line2: trimmed(160).optional(),
    postcode: trimmed(10).optional(),
    city: trimmed(80).optional(),
    state: trimmed(80).optional(),
    note: trimmed(1000).optional(),
    agree: z.literal('on', { message: 'Please agree to the terms to continue.' }),
  })
  .superRefine((data, ctx) => {
    if (data.fulfilment !== 'delivery') return
    if (!data.line1) ctx.addIssue({ code: 'custom', path: ['line1'], message: 'Enter the delivery address.' })
    if (!data.postcode || !/^\d{5}$/.test(data.postcode))
      ctx.addIssue({ code: 'custom', path: ['postcode'], message: 'Enter a 5-digit postcode.' })
    if (!data.city) ctx.addIssue({ code: 'custom', path: ['city'], message: 'Enter the town or city.' })
    if (!data.state) ctx.addIssue({ code: 'custom', path: ['state'], message: 'Enter the state.' })
  })

export const enquirySchema = z
  .object({
    name: nameSchema,
    email: z.union([emailSchema, z.literal('')]).optional(),
    phone: z.union([phoneSchema, z.literal('')]).optional(),
    topic: z.enum(['piece', 'visit', 'consign', 'order', 'other'], { message: 'Choose what your message is about.' }),
    message: trimmed(4000).min(5, 'Write a short message (at least 5 characters).'),
    product: z.coerce.number().int().positive().optional(),
    consent: z.literal('on', { message: 'Please agree so we can use your details to reply.' }),
  })
  .refine((d) => Boolean(d.email) || Boolean(d.phone), {
    path: ['email'],
    message: 'Give us an email address or a phone number so we can reply.',
  })

export const registerSchema = z.object({
  name: nameSchema,
  email: emailSchema,
  phone: z.union([phoneSchema, z.literal('')]).optional(),
  password: passwordSchema,
  marketingOptIn: z.literal('on').optional(),
})

export const loginSchema = z.object({
  email: emailSchema,
  password: z.string().min(1, 'Enter your password.').max(128),
})

export const resetSchema = z
  .object({
    token: z.string().min(10).max(200),
    password: passwordSchema,
    confirm: z.string(),
  })
  .refine((d) => d.password === d.confirm, { path: ['confirm'], message: 'The passwords do not match.' })

export const profileSchema = z.object({
  name: nameSchema,
  phone: z.union([phoneSchema, z.literal('')]).optional(),
  marketingOptIn: z.literal('on').optional(),
})

export type FieldErrors = Record<string, string>

/** Turn FormData into a plain object, dropping empty optional strings. */
export const formToObject = (form: FormData): Record<string, string> => {
  const out: Record<string, string> = {}
  for (const [k, v] of form.entries()) {
    if (typeof v === 'string') out[k] = v
  }
  return out
}

export const zodFieldErrors = (error: z.ZodError): FieldErrors => {
  const out: FieldErrors = {}
  for (const issue of error.issues) {
    const key = String(issue.path[0] ?? 'form')
    if (!out[key]) out[key] = issue.message
  }
  return out
}
