import preset from './src/tailwind-preset.js';
import type { Config } from 'tailwindcss';

/**
 * Storybook ビルド時に packages/ui 単独で Tailwind を回すための config。
 * web app の tailwind.config.ts と同じ preset を使い、 content scope は本パッケージの src のみ。
 */
export default {
  presets: [preset],
  content: ['./src/**/*.{ts,tsx}', './.storybook/**/*.{ts,tsx}'],
} satisfies Config;
