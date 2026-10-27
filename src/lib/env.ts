/** Central place for environment-derived settings, so defaults live in one file. */

/**
 * The site's public address. Read at runtime from SERVER_URL (NEXT_PUBLIC_* variables
 * are frozen into the build, which breaks images built in Docker or CI).
 */
export const getServerURL = (): string => {
  const url =
    process.env.SERVER_URL ||
    process.env.NEXT_PUBLIC_SERVER_URL ||
    (process.env.VERCEL_PROJECT_PRODUCTION_URL
      ? `https://${process.env.VERCEL_PROJECT_PRODUCTION_URL}`
      : '') ||
    'http://localhost:3000'
  return url.replace(/\/+$/, '')
}

export type BillplzConfig = {
  apiKey: string
  collectionId: string
  xSignatureKey: string
  baseURL: string
}

/** Billplz is configured only when all three secrets are present. */
export const getBillplzConfig = (): BillplzConfig | null => {
  const apiKey = process.env.BILLPLZ_API_KEY
  const collectionId = process.env.BILLPLZ_COLLECTION_ID
  const xSignatureKey = process.env.BILLPLZ_X_SIGNATURE_KEY
  if (!apiKey || !collectionId || !xSignatureKey) return null
  const sandbox = process.env.BILLPLZ_SANDBOX === 'true'
  const baseURL =
    process.env.BILLPLZ_API_URL ||
    (sandbox ? 'https://www.billplz-sandbox.com/api' : 'https://www.billplz.com/api')
  return { apiKey, collectionId, xSignatureKey, baseURL: baseURL.replace(/\/+$/, '') }
}

export const isProduction = process.env.NODE_ENV === 'production'
