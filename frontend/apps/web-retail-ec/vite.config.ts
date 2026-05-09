/// <reference types="vitest/config" />
import { fileURLToPath } from 'node:url';
import path from 'node:path';

import react from '@vitejs/plugin-react';
// vitest の test 設定を同一 config に書くために triple-slash で vitest の型 augmentation を入れ、
// defineConfig は vite から取る(vitest 3 + vite 6 の overload 解決で `test` が
// UserConfigExport から消えるケースを回避する canonical pattern)。
import { defineConfig } from 'vite';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src'),
    },
  },
  server: {
    port: 5173,
    proxy: {
      // BFF は別 process で 4001 起動。 dev 時は /graphql を proxy。 production は ALB で同 origin。
      '/graphql': {
        target: 'http://localhost:4001',
        changeOrigin: true,
      },
    },
  },
  build: {
    rollupOptions: {
      // OIDC silent renew 用 hidden iframe page を separate entry として bundle。
      // VITE_OIDC_SILENT_REDIRECT_URI が `/silent-renew.html` を指す前提。
      input: {
        main: path.resolve(__dirname, 'index.html'),
        silentRenew: path.resolve(__dirname, 'silent-renew.html'),
      },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test-setup.ts'],
  },
});
