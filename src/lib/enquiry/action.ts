'use server'
import { headers } from 'next/headers'
import { getPayloadClient } from '@/lib/payload'
import { rateLimit } from '@/lib/rate-limit'
import { clientIp } from '@/lib/request'
import { deliverEnquiry } from './deliver'
import { type EnquiryState, validate } from './schema'

const MIN_FILL_MS = 2500 // Faster than any person can complete the form.
const SUBMISSIONS_PER_WINDOW = 5
const WINDOW_MS = 10 * 60 * 1000

const fallbackMessage = (email: string) =>
  `Sorry — we could not send your enquiry just now. Your answers are still here, so please try again in a moment, or email ${email} directly.`

/**
 * Handles every website enquiry form. Returns success only after the enquiry
 * is stored in the CMS, where the admissions team sees it.
 */
export async function submitEnquiry(_prev: EnquiryState, formData: FormData): Promise<EnquiryState> {
  const raw = Object.fromEntries(
    ['formType', 'name', 'email', 'phone', 'stage', 'intendedStart', 'message', 'consentPrivacy', 'consentUpdates', 'sourcePath'].map((k) => [
      k,
      typeof formData.get(k) === 'string' ? (formData.get(k) as string) : undefined,
    ]),
  )
  const values = Object.fromEntries(Object.entries(raw).filter(([, v]) => typeof v === 'string')) as Record<string, string>
  const contactEmail = process.env.ENQUIRY_FALLBACK_EMAIL || 'info@isp.edu.my'

  // Spam controls: hidden honeypot field and a minimum completion time.
  const honeypot = formData.get('company_website')
  const startedAt = Number(formData.get('startedAt'))
  if ((typeof honeypot === 'string' && honeypot.length > 0) || !Number.isFinite(startedAt) || Date.now() - startedAt < MIN_FILL_MS) {
    return { status: 'error', message: fallbackMessage(contactEmail), values }
  }

  const ip = clientIp(await headers())
  if (!rateLimit(`enquiry:${ip}`, { limit: SUBMISSIONS_PER_WINDOW, windowMs: WINDOW_MS }).ok) {
    return {
      status: 'error',
      message: `We have received several enquiries from this connection in a short time. Please wait a few minutes, or email ${contactEmail}.`,
      values,
    }
  }

  const { data, errors } = validate(raw)
  if (!data) return { status: 'invalid', errors, values }

  const sourcePath = typeof raw.sourcePath === 'string' && raw.sourcePath.startsWith('/') ? raw.sourcePath.slice(0, 200) : ''

  try {
    const payload = await getPayloadClient()
    const enquiry = await payload.create({
      collection: 'enquiries',
      overrideAccess: true,
      data: {
        type: data.formType,
        parentName: data.name,
        email: data.email,
        phone: data.phone || undefined,
        stage: data.stage || undefined,
        intendedStart: data.intendedStart || undefined,
        message: data.message || undefined,
        consentPrivacy: true,
        consentUpdates: data.consentUpdates === 'on',
        sourcePath,
        status: 'new',
      },
    })
    // Notifications are best-effort: the enquiry is already safely stored.
    await deliverEnquiry(payload, enquiry)
    return { status: 'success', reference: `ISP-${String(enquiry.id).padStart(5, '0')}`, firstName: data.name.split(/\s+/)[0] }
  } catch (error) {
    const payload = await getPayloadClient().catch(() => null)
    payload?.logger.error({ err: error, msg: 'Failed to store website enquiry' })
    return { status: 'error', message: fallbackMessage(contactEmail), values }
  }
}
