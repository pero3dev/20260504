import type { Meta, StoryObj } from '@storybook/react';

import { Button } from './button.js';
import { Popover, PopoverContent, PopoverTrigger } from './popover.js';

const meta = {
  title: 'Components/Popover',
  component: Popover,
  tags: ['autodocs'],
} satisfies Meta<typeof Popover>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Detail: Story = {
  render: () => (
    <Popover>
      <PopoverTrigger asChild>
        <Button variant="ghost">詳細を表示</Button>
      </PopoverTrigger>
      <PopoverContent className="w-80">
        <p className="text-sm">
          注文 #12345 の詳細。 hover やフォーカスでは閉じず、 outside-click または ESC で閉じます。
        </p>
      </PopoverContent>
    </Popover>
  ),
};
