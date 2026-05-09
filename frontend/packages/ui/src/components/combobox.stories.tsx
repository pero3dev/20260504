import type { Meta, StoryObj } from '@storybook/react';
import { useState } from 'react';

import { Combobox, type ComboboxItem } from './combobox.js';

const SKU_ITEMS: ComboboxItem[] = [
  { value: 'SKU-0001', label: 'SKU-0001 - 牛乳 1L', keywords: ['牛乳', 'milk'] },
  { value: 'SKU-0002', label: 'SKU-0002 - 食パン 6 枚切り', keywords: ['パン', 'bread'] },
  { value: 'SKU-0003', label: 'SKU-0003 - 卵 10 個', keywords: ['卵', 'egg'] },
  { value: 'SKU-0004', label: 'SKU-0004 - バター 200g', keywords: ['バター', 'butter'] },
  {
    value: 'SKU-0005',
    label: 'SKU-0005 - チーズ スライス',
    keywords: ['チーズ', 'cheese'],
    disabled: true,
  },
];

function ControlledCombobox() {
  const [value, setValue] = useState<string | null>(null);
  return (
    <div className="w-80 p-6">
      <Combobox
        items={SKU_ITEMS}
        value={value}
        onChange={setValue}
        placeholder="SKU を選択..."
        searchPlaceholder="SKU を検索..."
        emptyMessage="該当する SKU がありません"
        ariaLabel="SKU 選択"
      />
      <p className="mt-2 text-sm text-muted-foreground">selected: {value ?? '(未選択)'}</p>
    </div>
  );
}

const meta = {
  title: 'Components/Combobox',
  component: ControlledCombobox,
  tags: ['autodocs'],
} satisfies Meta<typeof ControlledCombobox>;

export default meta;
type Story = StoryObj<typeof meta>;

export const SkuPicker: Story = {};
