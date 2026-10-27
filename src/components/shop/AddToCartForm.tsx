'use client'

import Link from 'next/link'
import React, { useActionState, useEffect, useRef, useState } from 'react'
import { useFormStatus } from 'react-dom'

import { addToCartAction } from '@/app/(frontend)/actions/cart'
import { idle, type ActionState } from '@/app/(frontend)/actions/types'
import { Icon } from '@/components/Icon'

const SubmitButton = ({ className = 'btn btn--block' }: { className?: string }) => {
  const { pending } = useFormStatus()
  return (
    <button type="submit" className={className} disabled={pending} aria-disabled={pending || undefined}>
      <Icon name="bag" />
      {pending ? 'Holding it for you…' : 'Add to cart'}
    </button>
  )
}

/**
 * Add to cart: holds this piece for the visitor. Shows the result in a live region
 * and a "View cart" link on success. On phones a bar with the price and the button
 * slides up once the main button has scrolled out of view.
 */
export const AddToCartForm = ({ productId, priceLabel, held = false }: { productId: number; priceLabel: string; held?: boolean }) => {
  const [state, action] = useActionState<ActionState, FormData>(addToCartAction, idle)
  const mainRef = useRef<HTMLDivElement>(null)
  const [mainVisible, setMainVisible] = useState(true)
  const [footerVisible, setFooterVisible] = useState(false)

  // The bar shows only once the main button is off screen, and steps aside for the footer.
  useEffect(() => {
    const el = mainRef.current
    if (!el || !('IntersectionObserver' in window)) return
    const main = new IntersectionObserver(([entry]) => setMainVisible(entry.isIntersecting), { threshold: 0 })
    main.observe(el)
    const footer = document.querySelector('.site-footer')
    const foot = new IntersectionObserver(([entry]) => setFooterVisible(entry.isIntersecting), { threshold: 0 })
    if (footer) foot.observe(footer)
    return () => {
      main.disconnect()
      foot.disconnect()
    }
  }, [])

  // After adding, the page re-renders with the piece held; this form stays mounted so its message is kept.
  const done = state.status === 'success' || held
  return (
    <form action={action} className="buy">
      <input type="hidden" name="productId" value={productId} />
      <div ref={mainRef} className="buy__main">
        {done ? (
          <Link href="/cart" className="btn btn--block">
            View cart <Icon name="arrow" />
          </Link>
        ) : (
          <SubmitButton />
        )}
      </div>
      <div className="buy__message" role={state.status === 'error' ? 'alert' : 'status'} aria-live="polite">
        {state.status !== 'idle' ? (
          <p className={state.status === 'error' ? 'buy__error' : 'buy__ok'}>{state.message}</p>
        ) : null}
      </div>
      <div className={`buy-bar${mainVisible || footerVisible ? '' : ' is-visible'}`}>
        <div className="buy-bar__price">
          <span className="price">{priceLabel}</span>
          {state.status === 'error' ? <span className="buy-bar__error">{state.message}</span> : null}
        </div>
        {done ? (
          <Link href="/cart" className="btn">
            View cart
          </Link>
        ) : (
          <SubmitButton className="btn" />
        )}
      </div>
    </form>
  )
}
