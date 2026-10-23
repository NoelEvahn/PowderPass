import type { Payload } from 'payload'
import type { Enquiry } from '@/payload-types'
import { STAGE_LABELS } from './schema'

const WEBHOOK_TIMEOUT_MS = 5000

const escapeHtml = (value: string) =>
  value.replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c] as string)

/** The JSON contract sent to ENQUIRY_WEBHOOK_URL (CRM / admissions platform integration point). */
export type EnquiryWebhookPayload = {
  event: 'enquiry.created'
  id: number
  createdAt: string
  type: Enquiry['type']
  name: string
  email: string
  phone: string | null
  stage: string | null
  intendedStart: string | null
  message: string | null
  consentUpdates: boolean
  sourcePath: string | null
  adminUrl: string
}

/**
 * Sends the staff notification email and/or forwards to the configured webhook,
 * then records what happened on the enquiry so staff can see delivery status.
 */
export async function deliverEnquiry(payload: Payload, enquiry: Enquiry) {
  const server = process.env.NEXT_PUBLIC_SERVER_URL || ''
  const adminUrl = `${server}/admin/collections/enquiries/${enquiry.id}`
  const delivery = { emailed: false, forwarded: false, error: '' }
  const problems: string[] = []

  const notifyTo = process.env.ENQUIRY_NOTIFY_TO
  if (process.env.SMTP_HOST && notifyTo) {
    try {
      const rows: [string, string | null | undefined][] = [
        ['Type', enquiry.type],
        ['Name', enquiry.parentName],
        ['Email', enquiry.email],
        ['Phone', enquiry.phone],
        ['Stage', enquiry.stage ? STAGE_LABELS[enquiry.stage as keyof typeof STAGE_LABELS] : null],
        ['Intended start', enquiry.intendedStart],
        ['Message', enquiry.message],
        ['Updates opt-in', enquiry.consentUpdates ? 'Yes' : 'No'],
        ['Submitted from', enquiry.sourcePath],
      ]
      await payload.sendEmail({
        to: notifyTo,
        replyTo: enquiry.email,
        subject: `New website ${enquiry.type} enquiry: ${enquiry.parentName}`,
        html: `<p>A new enquiry was submitted on the website.</p><table>${rows
          .filter(([, v]) => v)
          .map(([k, v]) => `<tr><th align="left">${k}</th><td>${escapeHtml(String(v)).replace(/\n/g, '<br>')}</td></tr>`)
          .join('')}</table><p><a href="${adminUrl}">Open in the website editor</a></p>`,
      })
      delivery.emailed = true
    } catch (error) {
      problems.push(`Email failed: ${(error as Error).message}`)
    }
  }

  const webhook = process.env.ENQUIRY_WEBHOOK_URL
  if (webhook) {
    const body: EnquiryWebhookPayload = {
      event: 'enquiry.created',
      id: enquiry.id,
      createdAt: enquiry.createdAt,
      type: enquiry.type,
      name: enquiry.parentName,
      email: enquiry.email,
      phone: enquiry.phone ?? null,
      stage: enquiry.stage ?? null,
      intendedStart: enquiry.intendedStart ?? null,
      message: enquiry.message ?? null,
      consentUpdates: Boolean(enquiry.consentUpdates),
      sourcePath: enquiry.sourcePath ?? null,
      adminUrl,
    }
    try {
      const res = await fetch(webhook, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(process.env.ENQUIRY_WEBHOOK_TOKEN ? { Authorization: `Bearer ${process.env.ENQUIRY_WEBHOOK_TOKEN}` } : {}),
        },
        body: JSON.stringify(body),
        signal: AbortSignal.timeout(WEBHOOK_TIMEOUT_MS),
      })
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      delivery.forwarded = true
    } catch (error) {
      problems.push(`Webhook failed: ${(error as Error).message}`)
    }
  }

  delivery.error = problems.join('; ')
  if (problems.length) payload.logger.warn({ msg: 'Enquiry stored but notification failed', enquiry: enquiry.id, problems })

  await payload.update({ collection: 'enquiries', id: enquiry.id, data: { delivery }, overrideAccess: true }).catch((error) => {
    payload.logger.error({ err: error, msg: 'Could not record enquiry delivery status' })
  })
}
