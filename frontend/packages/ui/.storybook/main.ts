import type { StorybookConfig } from '@storybook/react-vite';

/**
 * Storybook 8 / Vite 6 構成(ADR-0022 phase 5d / a11y 第 3 層)。
 *
 * <p>scope は packages/ui のみ。 web app の dashboard を docs 化する場合は
 * 各 web app に独立 Storybook を立てるか、 ここに alias を足して取り込む。
 */
const config: StorybookConfig = {
  framework: '@storybook/react-vite',
  stories: ['../src/**/*.stories.@(ts|tsx|mdx)'],
  addons: [
    '@storybook/addon-essentials',
    '@storybook/addon-a11y',
    '@storybook/addon-interactions',
  ],
  typescript: {
    // tsc は別タスクで走らせるので Storybook 自体は emit のみ。
    check: false,
  },
  docs: {
    autodocs: 'tag',
  },
};

export default config;
