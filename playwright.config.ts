import { defineConfig } from '@playwright/test';

/** E2E: builds the client, starts the authoritative server serving it, and drives real browsers. */
export default defineConfig({
  testDir: 'e2e',
  timeout: 240_000,
  expect: { timeout: 30_000 },
  workers: 1,
  reporter: [['list']],
  use: {
    baseURL: 'http://localhost:2599',
    launchOptions: { args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] },
  },
  webServer: {
    command: 'npm run build -w client && PORT=2599 STATIC_DIR=client/dist DATA_FILE= LOBBY_COUNTDOWN=1 node --import tsx server/src/index.ts',
    url: 'http://localhost:2599/health',
    timeout: 180_000,
    reuseExistingServer: false,
    stdout: 'pipe',
    stderr: 'pipe',
  },
});
