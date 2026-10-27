import tsconfigPaths from 'vite-tsconfig-paths'
import { defineConfig } from 'vitest/config'

export default defineConfig({
  plugins: [tsconfigPaths()],
  test: {
    environment: 'node',
    setupFiles: ['./vitest.setup.ts'],
    include: ['tests/unit/**/*.test.ts', 'tests/int/**/*.int.test.ts'],
    // Integration tests share one database; run files one at a time.
    fileParallelism: false,
    testTimeout: 60_000,
    hookTimeout: 120_000,
    server: { deps: { inline: [/server-only/] } },
    alias: { 'server-only': new URL('./tests/server-only-stub.ts', import.meta.url).pathname },
  },
})
