'use client'

import React, { useEffect, useState } from 'react'

import { formatRemaining, formatWhen } from '@/lib/when'

/**
 * "Held until Tuesday 27 October, 3:45pm" with a time-left line that updates every
 * 30 seconds. The server renders the fixed time and an empty, reserved second line,
 * so nothing shifts when the countdown starts.
 */
export const HoldCountdown = ({ expiresAt, label = 'Held for you until' }: { expiresAt: string; label?: string }) => {
  const [left, setLeft] = useState<string | null>(null)

  useEffect(() => {
    const tick = () => setLeft(formatRemaining(expiresAt) || 'ended')
    tick()
    const id = window.setInterval(tick, 30_000)
    return () => window.clearInterval(id)
  }, [expiresAt])

  return (
    <span className="hold">
      <span className="hold__until">
        {label} <time dateTime={expiresAt}>{formatWhen(expiresAt)}</time>
      </span>
      <span className="hold__left" aria-hidden={left ? undefined : true}>
        {left === null ? ' ' : left === 'ended' ? 'The hold has ended. Refresh to check.' : `${left} left`}
      </span>
    </span>
  )
}
