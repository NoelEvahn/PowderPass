import type { Metadata } from 'next'
import Link from 'next/link'
import { redirect } from 'next/navigation'
import React from 'react'

import { ForgotPasswordForm } from '@/components/account/ForgotPasswordForm'
import { getViewer } from '@/lib/session'

export const metadata: Metadata = { title: 'Reset your password', robots: { index: false, follow: false } }

export default async function ForgotPasswordPage() {
  if (await getViewer()) redirect('/account')
  return (
    <section className="section">
      <div className="container auth">
        <div className="stack">
          <h1>Reset your password</h1>
          <p className="lead">Enter your email and we will send you a link to choose a new password.</p>
        </div>
        <div className="card stack">
          <ForgotPasswordForm />
          <p className="small auth__alt">
            Remembered it? <Link href="/account/sign-in">Sign in</Link>.
          </p>
        </div>
      </div>
    </section>
  )
}
