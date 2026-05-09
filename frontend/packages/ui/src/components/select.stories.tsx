import type { Meta, StoryObj } from '@storybook/react';
import { useState } from 'react';

import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from './select.js';

const meta = {
  title: 'Components/Select',
  component: Select,
  tags: ['autodocs'],
} satisfies Meta<typeof Select>;

export default meta;
type Story = StoryObj<typeof meta>;

function ControlledSelect() {
  const [value, setValue] = useState('PLANNED');
  return (
    <div className="w-64 p-6">
      <Select value={value} onValueChange={setValue}>
        <SelectTrigger aria-label="ステータス">
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value="PLANNED">計画中</SelectItem>
          <SelectItem value="RELEASED">手配済</SelectItem>
          <SelectItem value="COMPLETED">完了</SelectItem>
          <SelectItem value="CANCELLED">キャンセル</SelectItem>
        </SelectContent>
      </Select>
      <p className="mt-2 text-sm text-muted-foreground">value: {value}</p>
    </div>
  );
}

export const Controlled: Story = {
  render: () => <ControlledSelect />,
};
