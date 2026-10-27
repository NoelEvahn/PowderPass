'use client'

import Link from 'next/link'
import React, { useActionState, useState } from 'react'

import { checkoutAction } from '@/app/(frontend)/actions/checkout'
import { idle, type ActionState } from '@/app/(frontend)/actions/types'
import { Icon } from '@/components/Icon'
import { MediaImage } from '@/components/MediaImage'
import { FormStatus, SelectField, SubmitButton, TextAreaField, TextField } from '@/components/forms/fields'
import { formatRM } from '@/lib/catalog'
import { formatWhen } from '@/lib/when'
import type { Media } from '@/payload-types'

export type CheckoutItem = { id: number; slug: string; title: string; brand: string; price: number; image: Media | null }

type Props = {
  items: CheckoutItem[]
  earliestHold: string
  signedIn: boolean
  defaults: { name: string; email: string; phone: string }
  whatsappHref: string
  pickupPlace: string
  settings: {
    onlinePayment: boolean
    payAtShop: boolean
    delivery: boolean
    deliveryFee: number
    deliveryNote: string
    payAtShopHoldHours: number
  }
}

const STATES = [
  'Johor', 'Kedah', 'Kelantan', 'Kuala Lumpur', 'Labuan', 'Melaka', 'Negeri Sembilan', 'Pahang', 'Penang', 'Perak', 'Perlis',
  'Putrajaya', 'Sabah', 'Sarawak', 'Selangor', 'Terengganu',
].map((s) => ({ label: s, value: s }))

const LABELS: Record<string, string> = {
  name: 'Name',
  email: 'Email',
  phone: 'Phone',
  paymentMethod: 'Payment',
  fulfilment: 'Collection or delivery',
  line1: 'Address',
  postcode: 'Postcode',
  city: 'Town or city',
  state: 'State',
  note: 'Note',
  agree: 'Terms',
}

const hoursLabel = (h: number) => (h >= 48 && h % 24 === 0 ? `${h / 24} days` : `${h} ${h === 1 ? 'hour' : 'hours'}`)

const FieldError = ({ id, message }: { id: string; message?: string }) =>
  message ? (
    <p id={`${id}-error`} className="field-error">
      {message}
    </p>
  ) : null

export const CheckoutForm = ({ items, earliestHold, signedIn, defaults, whatsappHref, pickupPlace, settings }: Props) => {
  const [state, action] = useActionState<ActionState, FormData>(checkoutAction, idle)
  const v = state.values ?? {}
  const errors = state.errors ?? {}

  const firstMethod: 'online' | 'pay_at_shop' = settings.onlinePayment ? 'online' : 'pay_at_shop'
  const [method, setMethod] = useState<'online' | 'pay_at_shop'>(
    v.paymentMethod === 'online' || v.paymentMethod === 'pay_at_shop' ? v.paymentMethod : firstMethod,
  )
  const [fulfilment, setFulfilment] = useState<'pickup' | 'delivery'>(v.fulfilment === 'delivery' ? 'delivery' : 'pickup')
  const [agree, setAgree] = useState(false)

  const canPay = settings.onlinePayment || settings.payAtShop
  const delivery = settings.delivery && method === 'online' && fulfilment === 'delivery'
  const subtotal = items.reduce((s, i) => s + i.price, 0)
  const fee = delivery ? settings.deliveryFee : 0
  const total = subtotal + fee

  const chooseMethod = (m: 'online' | 'pay_at_shop') => {
    setMethod(m)
    if (m !== 'online') setFulfilment('pickup')
  }

  return (
    <div className="checkout">
      {/* Without JavaScript the address fields are always shown ("only needed for delivery"). */}
      <noscript>
        <style>{'.checkout-address[hidden]{display:grid!important}'}</style>
      </noscript>
      <form action={action} className="checkout__form form" noValidate>
        <FormStatus state={state} labels={LABELS} />

        <fieldset className="checkout-section">
          <legend>
            <span className="checkout-section__n" aria-hidden="true">1</span> Your details
          </legend>
          {!signedIn ? (
            <p className="notice" style={{ marginBottom: 'var(--s-4)' }}>
              <Icon name="user" />
              <span>
                Have an account? <Link href="/account/sign-in?next=/checkout">Sign in</Link> to fill this in and keep your orders together.
                Or carry on as a guest.
              </span>
            </p>
          ) : null}
          <div className="form">
            <div className="form-row form-row--2">
              <TextField name="name" label="Full name" required autoComplete="name" defaultValue={v.name ?? defaults.name} error={errors.name} />
              <TextField
                name="phone"
                label="Phone / WhatsApp"
                type="tel"
                required
                autoComplete="tel"
                inputMode="tel"
                defaultValue={v.phone ?? defaults.phone}
                error={errors.phone}
              />
            </div>
            <TextField
              name="email"
              label="Email"
              type="email"
              required
              autoComplete="email"
              hint="We send your order confirmation here."
              defaultValue={v.email ?? defaults.email}
              error={errors.email}
            />
          </div>
        </fieldset>

        <fieldset className="checkout-section" id="paymentMethod" aria-describedby={errors.paymentMethod ? 'paymentMethod-error' : undefined}>
          <legend>
            <span className="checkout-section__n" aria-hidden="true">2</span> How would you like to pay?
          </legend>
          <div className="choice-group">
            <label className="choice">
              <input
                type="radio"
                name="paymentMethod"
                value="online"
                checked={method === 'online'}
                disabled={!settings.onlinePayment}
                onChange={() => chooseMethod('online')}
              />
              <span>
                <strong>Pay online now</strong>
                {settings.onlinePayment ? (
                  <span className="muted small">
                    FPX online banking (Maybank2u, HSBC, Public Bank and other banks), cards, and Touch &lsquo;n Go eWallet and other
                    e-wallets. Securely through Billplz.
                  </span>
                ) : (
                  <span className="muted small">Online payment is coming soon. For now, reserve your piece and pay at the boutique.</span>
                )}
              </span>
            </label>
            <label className="choice">
              <input
                type="radio"
                name="paymentMethod"
                value="pay_at_shop"
                checked={method === 'pay_at_shop'}
                disabled={!settings.payAtShop}
                onChange={() => chooseMethod('pay_at_shop')}
              />
              <span>
                <strong>Reserve now, pay at the boutique</strong>
                <span className="muted small">
                  {settings.payAtShop
                    ? `We hold your piece for ${hoursLabel(settings.payAtShopHoldHours)}. Pay in store when you collect it.`
                    : 'Paying at the boutique is not available right now.'}
                </span>
              </span>
            </label>
          </div>
          <FieldError id="paymentMethod" message={errors.paymentMethod} />
        </fieldset>

        <fieldset className="checkout-section" id="fulfilment">
          <legend>
            <span className="checkout-section__n" aria-hidden="true">3</span> {settings.delivery ? 'Collection or delivery' : 'Collection'}
          </legend>
          {settings.delivery ? (
            <>
              <div className="choice-group">
                <label className="choice">
                  <input
                    type="radio"
                    name="fulfilment"
                    value="pickup"
                    checked={fulfilment === 'pickup'}
                    onChange={() => setFulfilment('pickup')}
                  />
                  <span>
                    <strong>Collect at the boutique</strong>
                    <span className="muted small">{pickupPlace}. We will message you when it is ready.</span>
                  </span>
                </label>
                <label className="choice">
                  <input
                    type="radio"
                    name="fulfilment"
                    value="delivery"
                    checked={fulfilment === 'delivery' && method === 'online'}
                    disabled={method !== 'online'}
                    onChange={() => setFulfilment('delivery')}
                  />
                  <span>
                    <strong>
                      Delivery{settings.deliveryFee > 0 ? ` (${formatRM(settings.deliveryFee)})` : ' (free)'}
                    </strong>
                    <span className="muted small">
                      {method !== 'online' ? 'Delivery needs online payment.' : settings.deliveryNote || 'We send it to the address below.'}
                    </span>
                  </span>
                </label>
              </div>
              <FieldError id="fulfilment" message={errors.fulfilment} />
            </>
          ) : (
            <>
              <input type="hidden" name="fulfilment" value="pickup" />
              <p className="choice choice--static">
                <Icon name="store" />
                <span>
                  <strong>Collect at the boutique</strong>
                  <span className="muted small">{pickupPlace}. We will message you when your piece is ready.</span>
                </span>
              </p>
            </>
          )}

          {settings.delivery ? (
            <div className="checkout-address form" hidden={!delivery}>
              <p className="hint muted small checkout-address__hint">Only needed for delivery.</p>
              <TextField name="line1" label="Address" required={delivery} autoComplete="address-line1" defaultValue={v.line1} error={errors.line1} />
              <TextField name="line2" label="Apartment, unit or area" autoComplete="address-line2" defaultValue={v.line2} />
              <div className="form-row form-row--2">
                <TextField
                  name="postcode"
                  label="Postcode"
                  required={delivery}
                  autoComplete="postal-code"
                  inputMode="numeric"
                  maxLength={5}
                  defaultValue={v.postcode}
                  error={errors.postcode}
                />
                <TextField name="city" label="Town or city" required={delivery} autoComplete="address-level2" defaultValue={v.city} error={errors.city} />
              </div>
              <SelectField
                name="state"
                label="State"
                required={delivery}
                placeholder="Choose a state"
                options={STATES}
                defaultValue={v.state}
                error={errors.state}
              />
            </div>
          ) : null}
        </fieldset>

        <fieldset className="checkout-section">
          <legend>
            <span className="checkout-section__n" aria-hidden="true">4</span> Anything we should know?
          </legend>
          <TextAreaField name="note" label="Note" rows={3} maxLength={1000} defaultValue={v.note} error={errors.note} hint="For example, when you plan to come in." />
        </fieldset>

        <div className="stack">
          <div className="field">
            <label className="checkbox" htmlFor="agree">
              <input
                id="agree"
                type="checkbox"
                name="agree"
                checked={agree}
                onChange={(e) => setAgree(e.target.checked)}
                aria-invalid={errors.agree ? true : undefined}
                aria-describedby={errors.agree ? 'agree-error' : undefined}
                required
              />
              <span>
                I agree to the <Link href="/terms">Terms</Link> and <Link href="/refunds">Refunds &amp; returns</Link> policy, and I have read the{' '}
                <Link href="/privacy">Privacy notice</Link>.
              </span>
            </label>
            <FieldError id="agree" message={errors.agree} />
          </div>

          {canPay ? (
            <SubmitButton className="btn btn--block checkout__submit" pendingLabel={method === 'online' ? 'Taking you to payment…' : 'Reserving…'}>
              {method === 'online' ? 'Continue to payment' : 'Reserve my piece'}
            </SubmitButton>
          ) : (
            <div className="notice notice--pending">
              <Icon name="alert" />
              <span>
                Checkout is not available right now. <a href={whatsappHref} target="_blank" rel="noopener noreferrer">Message us on WhatsApp</a> and we
                will arrange your piece.
              </span>
            </div>
          )}
          <p className="meta checkout__safe">
            <Icon name="lock" size={16} />
            <span>Payments are processed securely by Billplz. We never see your card or bank details.</span>
          </p>
        </div>
      </form>

      <aside className="checkout__summary card" aria-labelledby="order-summary">
        <h2 id="order-summary" className="cart__h2">
          Your order
        </h2>
        <ul className="summary-items">
          {items.map((i) => (
            <li key={i.id} className="summary-item">
              <div className="summary-item__media">
                {i.image ? <MediaImage media={i.image} alt="" sizes="72px" /> : null}
              </div>
              <div className="summary-item__body">
                <p className="product-card__brand">{i.brand}</p>
                <p className="summary-item__title">{i.title}</p>
              </div>
              <p className="price">{formatRM(i.price)}</p>
            </li>
          ))}
        </ul>
        <dl className="totals">
          <div>
            <dt>Subtotal</dt>
            <dd className="price">{formatRM(subtotal)}</dd>
          </div>
          {delivery ? (
            <div>
              <dt>Delivery</dt>
              <dd className="price">{fee > 0 ? formatRM(fee) : 'Free'}</dd>
            </div>
          ) : null}
          <div className="totals__total">
            <dt>Total</dt>
            <dd className="price">{formatRM(total)}</dd>
          </div>
        </dl>
        <p className="meta">
          Your {items.length === 1 ? 'piece is' : 'pieces are'} held until <time dateTime={earliestHold}>{formatWhen(earliestHold)}</time>.
        </p>
        <Link href="/cart" className="link-arrow">
          <Icon name="arrow-left" size={16} /> Back to cart
        </Link>
      </aside>
    </div>
  )
}
