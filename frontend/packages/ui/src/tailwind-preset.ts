import tailwindcssAnimate from 'tailwindcss-animate';
import type { Config } from 'tailwindcss';

/**
 * 4 web app 共通の Tailwind preset。 各 app の `tailwind.config.ts` で
 *   presets: [preset]
 * として継承し、 content だけ app 固有に上書きする。 色 token は CSS 変数経由で
 * `@inventory/ui/styles.css` の :root に揃う。
 *
 * <p>`tailwindcss-animate` plugin を bundle し、 Radix の `data-[state=open]` 等で
 * Toast / Dialog / Select の fade / slide animation が動くようにする(F4 follow-up
 * phase B では plugin 未導入で削除されていたが本 phase で復元)。
 */
const preset: Partial<Config> = {
  theme: {
    extend: {
      colors: {
        border: 'hsl(var(--border))',
        background: 'hsl(var(--background))',
        foreground: 'hsl(var(--foreground))',
        primary: {
          DEFAULT: 'hsl(var(--primary))',
          foreground: 'hsl(var(--primary-foreground))',
        },
        muted: {
          DEFAULT: 'hsl(var(--muted))',
          foreground: 'hsl(var(--muted-foreground))',
        },
        destructive: {
          DEFAULT: 'hsl(var(--destructive))',
          foreground: 'hsl(var(--destructive-foreground))',
        },
      },
      borderRadius: {
        lg: 'var(--radius)',
      },
    },
  },
  plugins: [tailwindcssAnimate],
};

export default preset;
