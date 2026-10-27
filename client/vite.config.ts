import { defineConfig } from 'vite';
import { fileURLToPath } from 'node:url';

const shared = fileURLToPath(new URL('../shared/src', import.meta.url));

export default defineConfig({
  resolve: {
    alias: [
      { find: /^@doors\/shared$/, replacement: shared + '/index.ts' },
      { find: /^@doors\/shared\/(.*)$/, replacement: shared + '/$1' },
    ],
  },
  server: {
    host: true,
    port: 5173,
    proxy: { '/ws': { target: 'ws://localhost:2567', ws: true }, '/health': 'http://localhost:2567' },
  },
  preview: { host: true, port: 4173, proxy: { '/ws': { target: 'ws://localhost:2567', ws: true } } },
  build: {
    target: 'es2020',
    chunkSizeWarningLimit: 2500,
    rollupOptions: { output: { manualChunks: { three: ['three'], rapier: ['@dimforge/rapier3d-compat'] } } },
  },
  optimizeDeps: { exclude: ['@dimforge/rapier3d-compat'] },
});
