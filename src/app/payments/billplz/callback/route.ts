import { NextResponse } from 'next/server'

import { verifyCallback } from '@/lib/commerce/billplz'
import { confirmOnlinePayment } from '@/lib/commerce/orders'
import { getBillplzConfig } from '@/lib/env'
import { getPayloadClient } from '@/lib/payload'

export const dynamic = 'force-dynamic'

/** Billplz server-to-server callback (form-encoded, signed with the X Signature key). */
export async function POST(request: Request) {
  const cfg = getBillplzConfig()
  if (!cfg) return NextResponse.json({ error: 'Online payment is not configured' }, { status: 503 })

  let params: Record<string, string>
  try {
    const form = await request.formData()
    params = Object.fromEntries([...form.entries()].map(([k, v]) => [k, String(v)]))
  } catch {
    return NextResponse.json({ error: 'Bad request' }, { status: 400 })
  }

  if (!verifyCallback(params, cfg.xSignatureKey)) {
    return NextResponse.json({ error: 'Invalid signature' }, { status: 401 })
  }

  const payload = await getPayloadClient()
  if (params.paid === 'true' && params.id) {
    await confirmOnlinePayment(payload, {
      billId: params.id,
      paidAt: params.paid_at || null,
      transactionId: params.transaction_id || null,
      amountSen: params.paid_amount ? Number(params.paid_amount) : null,
    })
  }
  return new NextResponse('OK', { status: 200 })
}
