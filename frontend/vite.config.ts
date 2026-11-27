import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

// Backend used by the dev server proxy. Override with VITE_PROXY_TARGET, e.g. when the
// API runs in docker-compose or behind the Rancher Desktop ingress.
const proxyTarget = process.env.VITE_PROXY_TARGET ?? 'http://localhost:8080';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': { target: proxyTarget, changeOrigin: true },
    },
  },
  preview: {
    port: 4173,
    strictPort: true,
    proxy: {
      '/api': { target: proxyTarget, changeOrigin: true },
    },
  },
  build: {
    target: 'es2022',
    sourcemap: false,
    // Keep the CSP strict: no inline <script>/<style> injected by the bundler.
    assetsInlineLimit: 0,
  },
});
