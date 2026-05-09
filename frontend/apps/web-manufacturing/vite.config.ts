import { fileURLToPath } from 'node:url';
import path from 'node:path';

import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

// 純粋な vite 設定。 vitest の test 設定は `vitest.config.ts` に分離している
// (vite 6 + vitest 3 の defineConfig overload 解決で `test` 属性の型 augmentation
// が落ちることがあるため、 関心事を 2 ファイルに分ける)。
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src'),
    },
  },
  server: {
    port: 5174,
    proxy: {
      '/graphql': {
        target: 'http://localhost:4002',
        changeOrigin: true,
      },
    },
  },
  build: {
    rollupOptions: {
      input: {
        main: path.resolve(__dirname, 'index.html'),
        silentRenew: path.resolve(__dirname, 'silent-renew.html'),
      },
    },
  },
});
