import type { Metadata } from 'next'
import Link from 'next/link'
import { redirect } from 'next/navigation'
import React from 'react'

import { logoutAction } from '@/app/(frontend)/actions/account'
import { ProfileForm } from '@/components/account/ProfileForm'
import { Icon } from '@/components/Icon'
import { formatRM } from '@/lib/catalog'
import { getPayloadClient } from '@/lib/payload'
import { getViewer } from '@/lib/session'
import { formatDay } from '@/lib/when'
import type { Customer, Order } from '@/payload-types'

export const metadata: Metadata = {
  title: 'Your account',
  robots: { index: false, follow: false },
}

const STATUS: Record<Order['status'], { label: string; tone: string }> = {
  pending_payment: { label: 'Awaiting payment', tone: 'badge--held' },
  reserved: { label: 'Reserved', tone: 'badge--held' },
  paid: { label: 'Paid', tone: 'badge--success' },
  completed: { label: 'Completed', tone: 'badge--success' },
  cancelled: { label: 'Cancelled', tone: '' },
  expired: { label: 'Expired', tone: '' },
  refund_due: { label: 'Refund due', tone: 'badge--error' },
}

export default async function AccountPage({ searchParams }: { searchParams: Promise<Record<string, string | string[] | undefined>> }) {
  const sp = await searchParams
  const viewer = await getViewer()
  if (!viewer) redirect('/account/sign-in?next=/account')

  if (viewer.collection !== 'customers') {
    return (
      <section className="section">
        <div className="container stack account-narrow">
          <h1>Your account</h1>
          <div className="notice">
            <Icon name="info" />
            <span>You are signed in to the editor. Customer accounts are separate; use a private window to try the customer sign-in.</span>
          </div>
          <div>
            <Link className="btn" href="/admin">
              Open the editor <Icon name="arrow" />
            </Link>
          </div>
        </div>
      </section>
    )
  }

  const customer = viewer as Customer
  const payload = await getPayloadClient()
  const { docs: orders } = await payload.find({
    collection: 'orders',
    where: { customer: { equals: customer.id } },
    sort: '-createdAt',
    limit: 50,
    depth: 0,
    pagination: false,
  })
  const justReset = (Array.isArray(sp.reset) ? sp.reset[0] : sp.reset) === '1'

  return (
    <>
      <section className="page-hero">
        <div className="container">
          <h1>Hello, {customer.name.split(' ')[0]}</h1>
          <p className="lead">Your orders and details.</p>
        </div>
      </section>
      <section className="section section--tight">
        <div className="container account">
          <div className="stack account__main" style={{ ['--stack-gap' as string]: 'var(--s-6)' }}>
            {justReset ? (
              <div className="notice notice--success" role="status">
                <Icon name="check" />
                <span>Your password has been changed and you are signed in.</span>
              </div>
            ) : null}
            <section aria-labelledby="orders-h">
              <h2 id="orders-h" className="cart__h2">
                Your orders
              </h2>
              {orders.length === 0 ? (
                <div className="empty-state">
                  <h3>No orders yet</h3>
                  <p>When you reserve or buy a piece, it will appear here.</p>
                  <Link className="btn" href="/shop">
                    Browse the shop <Icon name="arrow" />
                  </Link>
                </div>
              ) : (
                <ul className="order-list">
                  {orders.map((o) => {
                    const s = STATUS[o.status]
                    return (
                      <li key={o.id}>
                        <Link href={`/order/${encodeURIComponent(o.orderNumber)}`} className="order-list__link">
                          <span className="order-list__main">
                            <strong>{o.orderNumber}</strong>
                            <span className="meta">
                              {formatDay(o.createdAt)} · {o.items.length} {o.items.length === 1 ? 'piece' : 'pieces'}
                            </span>
                          </span>
                          <span className={`badge ${s.tone}`}>{s.label}</span>
                          <span className="price">{formatRM(o.total)}</span>
                          <Icon name="arrow" size={18} />
                        </Link>
                      </li>
                    )
                  })}
                </ul>
              )}
            </section>
          </div>
          <aside className="account__side card" aria-labelledby="profile-h">
            <h2 id="profile-h" className="cart__h2">
              Your details
            </h2>
            <ProfileForm
              name={customer.name}
              email={customer.email}
              phone={customer.phone ?? ''}
              marketingOptIn={Boolean(customer.marketingOptIn)}
            />
            <form action={logoutAction}>
              <button type="submit" className="btn btn--secondary btn--block">
                Sign out
              </button>
            </form>
          </aside>
        </div>
      </section>
    </>
  )
}
