import { type APIRequestContext, expect, request as playwrightRequest, test } from '@playwright/test'

/**
 * Role-based access through the real REST API. Uses the seeded administrator
 * (SEED_ADMIN_EMAIL / SEED_ADMIN_PASSWORD) to create short-lived test accounts.
 */
const adminEmail = process.env.SEED_ADMIN_EMAIL!
const adminPassword = process.env.SEED_ADMIN_PASSWORD!

async function login(request: APIRequestContext, email: string, password: string) {
  const res = await request.post('/api/users/login', { data: { email, password } })
  expect(res.ok(), `login ${email}`).toBe(true)
  return (await res.json()).token as string
}

const auth = (token: string) => ({ Authorization: `JWT ${token}` })

test.describe.configure({ mode: 'serial' })
const baseURL = process.env.E2E_BASE_URL || 'http://localhost:3000'
/** A clean API client (no shared cookies). Browsers send Origin, which Payload's CSRF protection expects. */
const newApi = () => playwrightRequest.newContext({ baseURL, extraHTTPHeaders: { Origin: baseURL } })

let adminToken: string
const created: number[] = []
const stamp = Date.now()

test.beforeAll(async () => {
  test.skip(!adminEmail || !adminPassword, 'SEED_ADMIN_EMAIL / SEED_ADMIN_PASSWORD not set')
  const request = await newApi()
  adminToken = await login(request, adminEmail, adminPassword)
  for (const role of ['contributor', 'admissions']) {
    const res = await request.post('/api/users', {
      headers: auth(adminToken),
      data: { email: `${role}-${stamp}@example.com`, password: 'Test-password-123', name: `Test ${role}`, roles: [role] },
    })
    expect(res.ok(), await res.text()).toBe(true)
    created.push((await res.json()).doc.id)
  }
})

test.afterAll(async () => {
  const request = await newApi()
  for (const id of created) await request.delete(`/api/users/${id}`, { headers: auth(adminToken) })
})

test('a contributor can save drafts but cannot publish, delete or change roles', async () => {
  const request = await newApi()
  const token = await login(request, `contributor-${stamp}@example.com`, 'Test-password-123')
  const pages = await (await request.get('/api/pages?where[path][equals]=careers&depth=0', { headers: auth(token) })).json()
  const page = pages.docs[0]

  const draft = await request.patch(`/api/pages/${page.id}?draft=true`, { headers: auth(token), data: { title: 'Careers at ISP (draft edit)', _status: 'draft' } })
  expect(draft.ok()).toBe(true)

  // The draft is not visible to the public.
  const publicView = await (await request.get('/api/pages?where[path][equals]=careers&depth=0')).json()
  expect(publicView.docs[0].title).toBe('Careers at ISP')

  const publish = await request.patch(`/api/pages/${page.id}`, { headers: auth(token), data: { _status: 'published' } })
  expect(publish.ok()).toBe(false)

  const del = await request.delete(`/api/pages/${page.id}`, { headers: auth(token) })
  expect(del.status()).toBe(403)

  const me = (await (await request.get('/api/users/me', { headers: auth(token) })).json()).user
  const promote = await request.patch(`/api/users/${me.id}`, { headers: auth(token), data: { roles: ['admin'] } })
  const after = (await (await request.get('/api/users/me', { headers: auth(token) })).json()).user
  expect(promote.ok() ? after.roles : ['contributor']).toEqual(['contributor'])

  // Restore: an administrator discards the draft by re-publishing the original title.
  await request.patch(`/api/pages/${page.id}`, { headers: auth(adminToken), data: { title: 'Careers at ISP', _status: 'published' } })
})

test('the admissions role can read enquiries but not edit pages', async () => {
  const request = await newApi()
  const token = await login(request, `admissions-${stamp}@example.com`, 'Test-password-123')
  expect((await request.get('/api/enquiries', { headers: auth(token) })).ok()).toBe(true)
  const pages = await (await request.get('/api/pages?limit=1&depth=0', { headers: auth(token) })).json()
  const edit = await request.patch(`/api/pages/${pages.docs[0].id}`, { headers: auth(token), data: { title: 'x' } })
  expect(edit.status()).toBe(403)
})

test('publishing a change updates the public page', async ({ page }) => {
  const request = await newApi()
  const res = await (await request.get('/api/faqs?where[question][equals]=Do students wear a uniform?&depth=0', { headers: auth(adminToken) })).json()
  const faq = res.docs[0]
  await request.patch(`/api/faqs/${faq.id}`, { headers: auth(adminToken), data: { question: 'Do students wear a school uniform?', _status: 'published' } })
  await page.goto('/admissions/faqs')
  await expect(page.getByRole('button', { name: 'Do students wear a school uniform?' })).toBeVisible()
  await request.patch(`/api/faqs/${faq.id}`, { headers: auth(adminToken), data: { question: 'Do students wear a uniform?', _status: 'published' } })
})
