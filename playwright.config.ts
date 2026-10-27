import { defineConfig, devices } from '@playwright/test'

/**
 * End-to-end tests run against a production build on port 3100 with its own
 * database (E2E_DATABASE_URL), seeded by tests/e2e/global-setup.ts.
 *   pnpm build && pnpm test:e2e
 */
const PORT = Number(process.env.E2E_PORT || 3100)
const baseURL = process.env.E2E_BASE_URL || `http://localhost:${PORT}`
const databaseURL = process.env.E2E_DATABASE_URL || 'postgres://lt:lt@localhost:5432/labeltrunc_e2e'

export default defineConfig({
  testDir: './tests/e2e',
  forbidOnly: !!process.env.CI,
  retries: 0,
  workers: 1,
  fullyParallel: false,
  timeout: 60_000,
  expect: { timeout: 10_000 },
  reporter: [['list'], ['html', { open: 'never' }]],
  globalSetup: './tests/e2e/global-setup.ts',
  use: { baseURL, trace: 'retain-on-failure' },
  projects: [
    { name: 'desktop', use: { ...devices['Desktop Chrome'], viewport: { width: 1366, height: 900 } } },
    { name: 'mobile', use: { ...devices['Pixel 7'] } },
  ],
  webServer: process.env.E2E_BASE_URL
    ? undefined
    : {
        command: `pnpm exec next start -p ${PORT}`,
        url: `${baseURL}/robots.txt`,
        reuseExistingServer: true,
        timeout: 120_000,
        env: {
          DATABASE_URL: databaseURL,
          SERVER_URL: baseURL,
          PAYLOAD_SECRET: process.env.PAYLOAD_SECRET || 'e2e-secret-0123456789abcdef',
          MEDIA_DIR: 'media-e2e',
          NODE_ENV: 'production',
          CRON_SECRET: 'e2e-cron-secret',
        },
      },
})
