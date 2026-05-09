// Storybook の Vite ビルダから Tailwind を回すために必要。
// PostCSS を介して Tailwind directives(@tailwind base 等)を CSS に展開する。
export default {
  plugins: {
    tailwindcss: {},
    autoprefixer: {},
  },
};
