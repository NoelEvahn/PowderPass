import type { Payload } from 'payload'

import { formatRM } from '@/lib/catalog'
import { emailLayout, escapeHtml } from '@/lib/email-templates'
import { getServerURL } from '@/lib/env'
import type { Order } from '@/payload-types'

import { getSiteSettings } from './settings'

const orderLink = (order: Order) =>
  `${getServerURL()}/order/${encodeURIComponent(order.orderNumber)}?t=${encodeURIComponent(order.accessToken)}`

const itemsTable = (order: Order) => `
  <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="border-collapse:collapse;margin:16px 0;">
    ${order.items
      .map(
        (i) => `<tr><td style="padding:8px 0;border-bottom:1px solid #E0D3C3;">${escapeHtml(i.brand ? `${i.brand} · ` : '')}${escapeHtml(i.title)}</td>
        <td align="right" style="padding:8px 0;border-bottom:1px solid #E0D3C3;white-space:nowrap;">${escapeHtml(formatRM(i.price))}</td></tr>`,
      )
      .join('')}
    ${order.deliveryFee ? `<tr><td style="padding:8px 0;">Delivery</td><td align="right">${escapeHtml(formatRM(order.deliveryFee))}</td></tr>` : ''}
    <tr><td style="padding:8px 0;font-weight:bold;">Total</td><td align="right" style="font-weight:bold;">${escapeHtml(formatRM(order.total))}</td></tr>
  </table>`

const formatDateTime = (iso?: string | null) =>
  iso
    ? new Date(iso).toLocaleString('en-MY', {
        timeZone: 'Asia/Kuala_Lumpur',
        weekday: 'long',
        day: 'numeric',
        month: 'long',
        hour: 'numeric',
        minute: '2-digit',
      })
    : ''

const safeSend = async (payload: Payload, message: { to: string; subject: string; html: string; replyTo?: string }) => {
  try {
    await payload.sendEmail(message)
  } catch (err) {
    payload.logger.error({ err, to: message.to, subject: message.subject }, 'Email could not be sent')
  }
}

const staffInbox = async (payload: Payload) => {
  const site = await getSiteSettings(payload).catch(() => null)
  return {
    to: site?.notificationEmail || site?.email || process.env.STAFF_NOTIFICATION_EMAIL || '',
    shopEmail: site?.email || '',
    phone: site?.phone || '',
  }
}

export type OrderEmailKind = 'reserved' | 'paid' | 'expired' | 'refund_due' | 'cancelled'

export const sendOrderEmails = async (payload: Payload, order: Order, kind: OrderEmailKind) => {
  const staff = await staffInbox(payload)
  const link = orderLink(order)
  const contactLine = `Questions? Reply to this email${staff.phone ? ` or WhatsApp ${escapeHtml(staff.phone)}` : ''}.`

  if (kind === 'reserved') {
    await safeSend(payload, {
      to: order.contact.email,
      replyTo: staff.shopEmail || undefined,
      subject: `Reserved for you: order ${order.orderNumber}`,
      html: emailLayout(
        'Your piece is reserved',
        `<p>Hi ${escapeHtml(order.contact.name)}, thank you. We are holding your piece until <strong>${escapeHtml(formatDateTime(order.holdExpiresAt))}</strong>.</p>
         <p>Come to the boutique to pay and collect. It helps to message us first so we can have it ready.</p>
         ${itemsTable(order)}
         <p><a href="${escapeHtml(link)}">View your order</a></p><p>${contactLine}</p>`,
      ),
    })
  }

  if (kind === 'paid') {
    await safeSend(payload, {
      to: order.contact.email,
      replyTo: staff.shopEmail || undefined,
      subject: `Payment received: order ${order.orderNumber}`,
      html: emailLayout(
        'Thank you, your payment is confirmed',
        `<p>Hi ${escapeHtml(order.contact.name)}, we have received your payment.</p>
         <p>${order.fulfilment === 'delivery' ? 'We will contact you to arrange delivery.' : 'We will contact you to arrange collection from the boutique.'}</p>
         ${itemsTable(order)}
         <p><a href="${escapeHtml(link)}">View your order</a></p><p>${contactLine}</p>`,
      ),
    })
  }

  if (kind === 'expired' && order.paymentMethod === 'pay_at_shop') {
    await safeSend(payload, {
      to: order.contact.email,
      replyTo: staff.shopEmail || undefined,
      subject: `Your hold has ended: order ${order.orderNumber}`,
      html: emailLayout(
        'Your hold has ended',
        `<p>Hi ${escapeHtml(order.contact.name)}, the hold on order ${escapeHtml(order.orderNumber)} has ended and the piece is back in the shop.</p>
         <p>If you still want it, message us and we will check whether it is available.</p><p>${contactLine}</p>`,
      ),
    })
  }

  if (!staff.to) return
  const subjects: Record<OrderEmailKind, string> = {
    reserved: `New reservation (pay at boutique): ${order.orderNumber}`,
    paid: `Paid online: ${order.orderNumber}`,
    expired: `Hold expired: ${order.orderNumber}`,
    refund_due: `ACTION NEEDED, refund due: ${order.orderNumber}`,
    cancelled: `Order cancelled by customer: ${order.orderNumber}`,
  }
  if (kind === 'expired' && order.paymentMethod === 'online') return
  await safeSend(payload, {
    to: staff.to,
    subject: subjects[kind],
    html: emailLayout(
      subjects[kind],
      `<p><strong>${escapeHtml(order.contact.name)}</strong> · ${escapeHtml(order.contact.email)} · ${escapeHtml(order.contact.phone)}</p>
       <p>${order.paymentMethod === 'online' ? 'Paid online' : 'Pays at the boutique'} · ${order.fulfilment === 'delivery' ? 'Delivery' : 'Collect at boutique'}</p>
       ${kind === 'refund_due' ? '<p style="color:#9B1C1C;"><strong>A payment arrived after the hold ended and the piece is no longer available. Please refund the customer and contact them.</strong></p>' : ''}
       ${itemsTable(order)}
       ${order.customerNote ? `<p>Customer note: ${escapeHtml(order.customerNote)}</p>` : ''}
       <p><a href="${escapeHtml(`${getServerURL()}/admin/collections/orders/${order.id}`)}">Open in the editor</a></p>`,
    ),
  })
}

export const sendEnquiryAlert = async (
  payload: Payload,
  enquiry: { id: number | string; name: string; email?: string | null; phone?: string | null; topic: string; message: string },
) => {
  const staff = await staffInbox(payload)
  if (!staff.to) return
  await safeSend(payload, {
    to: staff.to,
    replyTo: enquiry.email || undefined,
    subject: `New enquiry from ${enquiry.name}`,
    html: emailLayout(
      'New website enquiry',
      `<p><strong>${escapeHtml(enquiry.name)}</strong>${enquiry.email ? ` · ${escapeHtml(enquiry.email)}` : ''}${enquiry.phone ? ` · ${escapeHtml(enquiry.phone)}` : ''}</p>
       <p>Topic: ${escapeHtml(enquiry.topic)}</p>
       <p style="white-space:pre-wrap;">${escapeHtml(enquiry.message)}</p>
       <p><a href="${escapeHtml(`${getServerURL()}/admin/collections/enquiries/${enquiry.id}`)}">Open in the editor</a></p>`,
    ),
  })
}
