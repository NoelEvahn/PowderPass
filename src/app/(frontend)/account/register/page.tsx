import type { Metadata } from 'next'
import Link from 'next/link'
import { redirect } from 'next/navigation'
import React from 'react'

import { RegisterForm } from '@/components/account/RegisterForm'
import { getViewer } from '@/lib/session'

export const metadata: Metadata = { title: 'Create an account', robots: { index: false, follow: false } }

const safeNext = (value: string | string[] | undefined) => {
  const v = Array.isArray(value) ? value[0] : value
  return v && v.startsWith('/') && !v.startsWith('//') && !v.startsWith('/admin') ? v : '/account'
}

export default async function RegisterPage({ searchParams }: { searchParams: Promise<Record<string, string | string[] | undefined>> }) {
  const next = safeNext((await searchParams).next)
  if (await getViewer()) redirect('/account')
  return (
    <section className="section">
      <div className="container auth">
        <div className="stack">
          <h1>Create an account</h1>
          <p className="lead">Keep your orders in one place and check out faster. You can also buy as a guest.</p>
        </div>
        <div className="card stack">
          <RegisterForm next={next} />
          <p className="small auth__alt">
            Already have an account? <Link href={`/account/sign-in${next !== '/account' ? `?next=${encodeURIComponent(next)}` : ''}`}>Sign in</Link>.
          </p>
        </div>
      </div>
    </section>
  )
}
