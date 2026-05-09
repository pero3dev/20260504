import type { Meta, StoryObj } from '@storybook/react';

import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from './command.js';

const meta = {
  title: 'Components/Command',
  component: Command,
  tags: ['autodocs'],
} satisfies Meta<typeof Command>;

export default meta;
type Story = StoryObj<typeof meta>;

export const SkuSearch: Story = {
  render: () => (
    <div className="w-80 rounded-md border border-border">
      <Command>
        <CommandInput placeholder="SKU を検索..." />
        <CommandList>
          <CommandEmpty>該当なし</CommandEmpty>
          <CommandGroup heading="人気の SKU">
            <CommandItem value="SKU-0001" keywords={['牛乳', 'milk']}>
              SKU-0001 - 牛乳 1L
            </CommandItem>
            <CommandItem value="SKU-0002" keywords={['パン', 'bread']}>
              SKU-0002 - 食パン 6 枚切り
            </CommandItem>
            <CommandItem value="SKU-0003" keywords={['卵', 'egg']}>
              SKU-0003 - 卵 10 個
            </CommandItem>
          </CommandGroup>
        </CommandList>
      </Command>
    </div>
  ),
};
