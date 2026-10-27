'use server'

import { sendEnquiryAlert } from '@/lib/commerce/notify'
import { getPayloadClient } from '@/lib/payload'
import { rateLimit } from '@/lib/rate-limit'
import { getClientIP } from '@/lib/session'
import { enquirySchema, formToObject, zodFieldErrors } from '@/lib/validation'

import type { ActionState } from './types'

export async function enquiryAction(_prev: ActionState, form: FormData): Promise<ActionState> {
  const raw = formToObject(form)
  const values = { ...raw }
  delete values.consent
  delete values.website
  delete values.startedAt

  // Spam controls without a CAPTCHA: a hidden field and a minimum fill time.
  const startedAt = Number(raw.startedAt)
  const tooFast = Number.isFinite(startedAt) && Date.now() - startedAt < 2500
  if (raw.website || tooFast) {
    return { status: 'success', message: 'Thank you. We will reply as soon as we can.' }
  }
  if (!rateLimit(`enquiry:${await getClientIP()}`, 5, 30 * 60_000).ok) {
    return { status: 'error', message: 'Too many messages in a short time. Please wait, or message us on WhatsApp.', values }
  }

  const parsed = enquirySchema.safeParse(raw)
  if (!parsed.success) {
    return { status: 'error', message: 'Please check the highlighted fields.', errors: zodFieldErrors(parsed.error), values }
  }

  const payload = await getPayloadClient()
  const d = parsed.data
  let productId: number | undefined
  if (d.product) {
    const { totalDocs } = await payload.count({ collection: 'products', where: { id: { equals: d.product } } })
    if (totalDocs) productId = d.product
  }
  try {
    const enquiry = await payload.create({
      collection: 'enquiries',
      data: {
        name: d.name,
        email: d.email || undefined,
        phone: d.phone || undefined,
        topic: d.topic,
        message: d.message,
        product: productId,
        status: 'new',
      },
    })
    await sendEnquiryAlert(payload, enquiry)
  } catch (err) {
    payload.logger.error({ err }, 'Enquiry could not be saved')
    return { status: 'error', message: 'Your message could not be sent. Please try again, or message us on WhatsApp.', values }
  }
  return { status: 'success', message: 'Thank you. Your message is with us and we will reply as soon as we can.' }
}
