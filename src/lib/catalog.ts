/** Shared option lists, so the editor and the shop use the same wording. */

export const CONDITION_OPTIONS = [
  { label: 'Brand new', value: 'new' },
  { label: 'Unused (never carried)', value: 'unused' },
  { label: 'Pre-loved: excellent', value: 'excellent' },
  { label: 'Pre-loved: very good', value: 'very-good' },
  { label: 'Pre-loved: good', value: 'good' },
  { label: 'Pre-loved: fair', value: 'fair' },
] as const

export type ConditionValue = (typeof CONDITION_OPTIONS)[number]['value']

export const INCLUDES_OPTIONS = [
  { label: 'Dust bag', value: 'dust-bag' },
  { label: 'Box', value: 'box' },
  { label: 'Receipt', value: 'receipt' },
  { label: 'Authenticity card', value: 'authenticity-card' },
  { label: 'Care booklet', value: 'care-booklet' },
  { label: 'Strap', value: 'strap' },
  { label: 'Lock and keys', value: 'lock-keys' },
  { label: 'Rain cover', value: 'rain-cover' },
  { label: 'Ribbon / flower', value: 'ribbon' },
] as const

export const PRODUCT_STATUS_OPTIONS = [
  { label: 'Available', value: 'available' },
  { label: 'Sold', value: 'sold' },
  { label: 'Archived (hidden from shop)', value: 'archived' },
] as const

export const labelFor = (
  options: readonly { label: string; value: string }[],
  value: string | null | undefined,
): string => options.find((o) => o.value === value)?.label ?? ''

export const formatRM = (amount: number | null | undefined): string => {
  const n = typeof amount === 'number' && Number.isFinite(amount) ? amount : 0
  return `RM ${n.toLocaleString('en-MY', {
    minimumFractionDigits: Number.isInteger(n) ? 0 : 2,
    maximumFractionDigits: 2,
  })}`
}

/** Convert ringgit to sen without floating point drift. */
export const toSen = (amount: number): number => Math.round(amount * 100)
