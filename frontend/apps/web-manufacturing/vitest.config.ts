import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

// vitest 専用設定。 vite.config.ts と分離して `test` 属性の型 augmentation を確実に効かす。
// vite plugin は vitest が自動 merge するが、 react JSX を含む test を扱うため明示する。
export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test-setup.ts'],
  },
});
