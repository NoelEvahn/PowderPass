import type { Metadata } from 'next'
import Link from 'next/link'
import React from 'react'

import { ResetPasswordForm } from '@/components/account/ResetPasswordForm'

export const metadata: Metadata = { title: 'Choose a new password', robots: { index: false, follow: false } }

export default async function ResetPasswordPage({ searchParams }: { searchParams: Promise<Record<string, string | string[] | undefined>> }) {
  const raw = (await searchParams).token
  const token = (Array.isArray(raw) ? raw[0] : raw) ?? ''
  return (
    <section className="section">
      <div className="container auth">
        <div className="stack">
          <h1>Choose a new password</h1>
        </div>
        {token ? (
          <div className="card stack">
            <ResetPasswordForm token={token} />
          </div>
        ) : (
          <div className="notice notice--error">
            <span>
              This reset link is missing its code. Open the link from your email again, or <Link href="/account/forgot-password">ask for a new one</Link>.
            </span>
          </div>
        )}
      </div>
    </section>
  )
}
