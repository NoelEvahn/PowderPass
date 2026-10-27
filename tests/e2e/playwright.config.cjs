// Browser tests against a LOCAL WordPress (default http://localhost:8080). Never point at the live site.
const { defineConfig } = require('@playwright/test');
const base = process.env.LT_BASE_URL || 'http://localhost:8080';
if (!/localhost|127\.0\.0\.1/.test(base)) throw new Error('LT_BASE_URL must be a local site.');
module.exports = defineConfig({
  testDir: __dirname,
  testMatch: /.*\.spec\.cjs/,
  workers: 1,
  timeout: 60000,
  reporter: [['list']],
  use: { baseURL: base, trace: 'retain-on-failure' },
});
