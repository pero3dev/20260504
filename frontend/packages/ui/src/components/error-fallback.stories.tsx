import type { Meta, StoryObj } from '@storybook/react';

import { DefaultErrorFallback } from './error-fallback.js';

const meta = {
  title: 'Components/ErrorFallback',
  component: DefaultErrorFallback,
  tags: ['autodocs'],
} satisfies Meta<typeof DefaultErrorFallback>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
  args: {
    error: new Error('inventory-read-model: connect ECONNREFUSED 10.0.0.42:6379'),
    resetErrorBoundary: () => {
      window.alert('reset triggered');
    },
  },
};
