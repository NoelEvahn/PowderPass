import nextCoreWebVitals from 'eslint-config-next/core-web-vitals'
import nextTypescript from 'eslint-config-next/typescript'

const eslintConfig = [
  ...nextCoreWebVitals,
  ...nextTypescript,
  {
    rules: {
      '@typescript-eslint/no-unused-vars': ['warn', { argsIgnorePattern: '^_', varsIgnorePattern: '^_', caughtErrorsIgnorePattern: '^(_|ignore)' }],
    },
  },
  {
    ignores: ['.next/', 'media/', 'src/migrations/', '.seed-cache/', 'test-results/', 'playwright-report/', 'src/payload-types.ts', 'src/app/(payload)/admin/importMap.js'],
  },
]

export default eslintConfig
