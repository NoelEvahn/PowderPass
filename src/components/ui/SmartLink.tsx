import Link from 'next/link'
import type { AnchorHTMLAttributes, ReactNode } from 'react'

type Props = Omit<AnchorHTMLAttributes<HTMLAnchorElement>, 'href'> & {
  href: string
  newTab?: boolean
  children: ReactNode
}

/** Client-side navigation for internal paths; plain anchors (optionally new tab) for everything else. */
export function SmartLink({ href, newTab, children, ...rest }: Props) {
  const announce = newTab ? <span className="visually-hidden"> (opens in a new tab)</span> : null
  if (href.startsWith('/') && !newTab) {
    return (
      <Link href={href} {...rest}>
        {children}
      </Link>
    )
  }
  return (
    <a href={href} {...rest} {...(newTab ? { target: '_blank', rel: 'noopener noreferrer' } : {})}>
      {children}
      {announce}
    </a>
  )
}
