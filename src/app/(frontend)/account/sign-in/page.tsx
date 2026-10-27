import type { Metadata } from 'next'
import Link from 'next/link'
import { redirect } from 'next/navigation'
import React from 'react'

import { LoginForm } from '@/components/account/LoginForm'
import { getViewer } from '@/lib/session'

export const metadata: Metadata = { title: 'Sign in', robots: { index: false, follow: false } }

const safeNext = (value: string | string[] | undefined) => {
  const v = Array.isArray(value) ? value[0] : value
  return v && v.startsWith('/') && !v.startsWith('//') && !v.startsWith('/admin') ? v : '/account'
}

export default async function SignInPage({ searchParams }: { searchParams: Promise<Record<string, string | string[] | undefined>> }) {
  const next = safeNext((await searchParams).next)
  if (await getViewer()) redirect(next === '/account' ? '/account' : next)
  return (
    <section className="section">
      <div className="container auth">
        <div className="stack">
          <h1>Sign in</h1>
          <p className="lead">See your orders and check out faster.</p>
        </div>
        <div className="card stack">
          <LoginForm next={next} />
          <p className="small auth__alt">
            New here? <Link href={`/account/register${next !== '/account' ? `?next=${encodeURIComponent(next)}` : ''}`}>Create an account</Link>
            {next === '/checkout' ? (
              <>
                {' '}
                or <Link href="/checkout">continue as a guest</Link>
              </>
            ) : null}
            .
          </p>
        </div>
      </div>
    </section>
  )
}
