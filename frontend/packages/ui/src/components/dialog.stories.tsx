import type { Meta, StoryObj } from '@storybook/react';

import { Button } from './button.js';
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogTitle,
  DialogTrigger,
} from './dialog.js';

const meta = {
  title: 'Components/Dialog',
  component: Dialog,
  tags: ['autodocs'],
} satisfies Meta<typeof Dialog>;

export default meta;
type Story = StoryObj<typeof meta>;

export const ConfirmDialog: Story = {
  render: () => (
    <Dialog>
      <DialogTrigger asChild>
        <Button variant="secondary">削除</Button>
      </DialogTrigger>
      <DialogContent>
        <DialogTitle>本当に削除しますか?</DialogTitle>
        <DialogDescription>
          この操作は元に戻せません。 関連する在庫予約も同時に解放されます。
        </DialogDescription>
        <DialogFooter>
          <DialogClose asChild>
            <Button variant="ghost">キャンセル</Button>
          </DialogClose>
          <DialogClose asChild>
            <Button>削除する</Button>
          </DialogClose>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  ),
};
