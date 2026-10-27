import { execFileSync } from 'child_process'
import fs from 'fs'
import path from 'path'
import sharp from 'sharp'

import type { FullConfig } from '@playwright/test'

export const ADMIN = { email: 'e2e-admin@example.com', password: 'e2e-admin-password-1' }
export const STATE_FILE = path.resolve(process.cwd(), 'tests/e2e/.state.json')

const testImage = (label: string, colour: string) =>
  sharp({ create: { width: 800, height: 1000, channels: 3, background: colour } })
    .composite([
      {
        input: Buffer.from(
          `<svg width="800" height="1000"><text x="50%" y="50%" font-size="60" text-anchor="middle" fill="#fff" font-family="sans-serif">${label}</text></svg>`,
        ),
      },
    ])
    .png()
    .toBuffer()

/** Seeds the e2e database with the real site content plus clearly labelled test pieces. */
export default async function globalSetup(config: FullConfig) {
  const baseURL = config.projects[0].use.baseURL!
  const databaseURL = process.env.E2E_DATABASE_URL || 'postgres://lt:lt@localhost:5432/labeltrunc_e2e'

  // First request starts Payload, which applies migrations.
  for (let i = 0; i < 30; i++) {
    const res = await fetch(`${baseURL}/sitemap.xml`).catch(() => null)
    if (res?.ok) break
    await new Promise((r) => setTimeout(r, 1000))
  }

  execFileSync('pnpm', ['exec', 'payload', 'run', 'src/seed/index.ts'], {
    stdio: 'inherit',
    env: {
      ...process.env,
      DATABASE_URL: databaseURL,
      PAYLOAD_DB_PUSH: 'false',
      SEED_ADMIN_EMAIL: ADMIN.email,
      SEED_ADMIN_PASSWORD: ADMIN.password,
      NODE_OPTIONS: '--no-deprecation',
    },
  })

  const login = await fetch(`${baseURL}/api/users/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(ADMIN),
  })
  if (!login.ok) throw new Error(`Admin login failed: ${login.status} ${await login.text()}`)
  const { token } = (await login.json()) as { token: string }
  const auth = { Authorization: `JWT ${token}` }

  const api = async <T>(pathname: string, body: unknown): Promise<T> => {
    const res = await fetch(`${baseURL}${pathname}`, {
      method: 'POST',
      headers: { ...auth, 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    })
    if (!res.ok) throw new Error(`${pathname} failed: ${res.status} ${await res.text()}`)
    return ((await res.json()) as { doc: T }).doc
  }

  const upload = async (label: string, colour: string) => {
    const form = new FormData()
    form.append('file', new Blob([new Uint8Array(await testImage(label, colour))], { type: 'image/png' }), `${label}.png`)
    form.append('_payload', JSON.stringify({ alt: `Automated test image: ${label}` }))
    const res = await fetch(`${baseURL}/api/media`, { method: 'POST', headers: auth, body: form })
    if (!res.ok) throw new Error(`upload failed: ${res.status} ${await res.text()}`)
    return ((await res.json()) as { doc: { id: number } }).doc.id
  }

  const brand = await api<{ id: number }>('/api/brands', { name: 'E2E Test House' })
  const img1 = await upload('E2E TEST 1', '#6b4a52')
  const img2 = await upload('E2E TEST 2', '#4a5a6b')

  const product = (data: Record<string, unknown>) =>
    api<{ id: number; slug: string }>('/api/products?draft=false', {
      brand: brand.id,
      condition: 'excellent',
      gallery: [img1, img2],
      includes: ['dust-bag', 'box'],
      colour: 'Burgundy',
      _status: 'published',
      ...data,
    })

  const available = await product({ title: 'E2E Test Flap Bag', price: 12500, featured: true })
  const second = await product({ title: 'E2E Test Tote', price: 4800 })
  const boutique = await product({ title: 'E2E Test Boutique Only', price: 30000, sellOnline: false })
  const sold = await product({ title: 'E2E Test Sold Bag', price: 9000, status: 'sold' })

  fs.writeFileSync(STATE_FILE, JSON.stringify({ token, available, second, boutique, sold }, null, 2))
}
