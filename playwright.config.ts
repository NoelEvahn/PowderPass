import 'dotenv/config'
import { defineConfig, devices } from '@playwright/test'

/**
 * End-to-end tests run against a seeded site (npm run seed).
 * Set E2E_BASE_URL to test a deployed environment; otherwise a local server is expected on :3000.
 * CHROME_PATH lets the suite use a preinstalled Chromium.
 */
const launchOptions = process.env.CHROME_PATH ? { executablePath: process.env.CHROME_PATH } : {}

export default defineConfig({
  testDir: 'tests/e2e',
  timeout: 60_000,
  expect: { timeout: 10_000 },
  fullyParallel: true,
  retries: process.env.CI ? 1 : 0,
  reporter: [['list']],
  use: {
    baseURL: process.env.E2E_BASE_URL || 'http://localhost:3000',
    trace: 'retain-on-failure',
    launchOptions,
  },
  projects: [
    { name: 'desktop', use: { ...devices['Desktop Chrome'], viewport: { width: 1440, height: 900 }, launchOptions } },
    // API-level role checks are device-independent and share test accounts, so they run once.
    { name: 'mobile', use: { ...devices['Pixel 7'], launchOptions }, testIgnore: /cms-roles/ },
  ],
})
