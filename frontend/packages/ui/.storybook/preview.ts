import type { Preview } from '@storybook/react';

import '../src/styles.css';

/**
 * 全 stories に効くグローバル parameters。
 *
 * <ul>
 *   <li>`a11y`: addon-a11y(axe-core)を全 story で実行。 violation は Stories panel に表示
 *   <li>`controls`: 色 / 文字列を expandable に
 *   <li>`backgrounds`: design system 背景を default に固定
 * </ul>
 */
const preview: Preview = {
  parameters: {
    actions: { argTypesRegex: '^on[A-Z].*' },
    controls: {
      matchers: {
        color: /(background|color)$/i,
        date: /Date$/,
      },
    },
    backgrounds: {
      default: 'app',
      values: [{ name: 'app', value: 'hsl(0 0% 100%)' }],
    },
    a11y: {
      // axe rule の override が必要になったらここで disable する(原則は default 全有効)。
      config: {
        rules: [],
      },
    },
  },
};

export default preview;
