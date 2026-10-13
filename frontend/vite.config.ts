import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

// Dev server: port 5176 (strict) so it never collides with the Docker frontend (5175) or other
// local projects (5173/5174). /api is proxied to the host-run or Docker backend on 8081.
const proxyTarget = process.env.VITE_DEV_PROXY_TARGET ?? 'http://localhost:8081';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5176,
    strictPort: true,
    proxy: {
      '/api': {
        target: proxyTarget,
        changeOrigin: true,
        // Ask calls on a CPU-only local model can take minutes.
        timeout: 300_000,
        proxyTimeout: 300_000,
      },
    },
  },
  preview: {
    port: 5176,
    strictPort: true,
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./tests/setup.ts'],
    include: ['tests/**/*.test.{ts,tsx}'],
    css: false,
    // jsdom workers are memory-hungry; two keeps the suite fast on an 8 GB laptop and on CI runners.
    pool: 'forks',
    maxWorkers: 2,
    minWorkers: 1,
    testTimeout: 15_000,
    env: {
      VITE_POLL_INTERVAL_MS: '50',
      VITE_API_BASE_URL: 'http://localhost/api/v1',
    },
  },
});
