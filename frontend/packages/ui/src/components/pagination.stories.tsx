import type { Meta, StoryObj } from '@storybook/react';

import { Pagination } from './pagination.js';

const meta = {
  title: 'Components/Pagination',
  component: Pagination,
  tags: ['autodocs'],
  args: {
    prevLabel: '前へ',
    nextLabel: '次へ',
    ariaLabel: 'ページ送り',
    pageInfo: '11 - 20 / 134',
    onPrev: () => {},
    onNext: () => {},
  },
} satisfies Meta<typeof Pagination>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Middle: Story = { args: { hasPrev: true, hasNext: true } };
export const FirstPage: Story = { args: { hasPrev: false, hasNext: true } };
export const LastPage: Story = { args: { hasPrev: true, hasNext: false } };
export const Pending: Story = {
  args: { hasPrev: true, hasNext: true, isPending: true, pageInfo: '取得中...' },
};
