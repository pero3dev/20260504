import type { Meta, StoryObj } from '@storybook/react';

import { Button } from './button.js';
import { ToastProvider, useToast } from './toast.js';

/**
 * Toast の発火デモ。 useToast() は ToastProvider 配下からのみ呼べるため、
 * meta.decorators で全 stories を Provider でラップする。
 */
const meta = {
  title: 'Components/Toast',
  component: ToastProvider,
  tags: ['autodocs'],
  decorators: [
    (Story) => (
      <ToastProvider>
        <Story />
      </ToastProvider>
    ),
  ],
} satisfies Meta<typeof ToastProvider>;

export default meta;
type Story = StoryObj<typeof meta>;

function ToastDemo() {
  const { toast } = useToast();
  return (
    <div className="flex flex-wrap gap-2 p-6">
      <Button onClick={() => toast({ title: '保存しました', variant: 'success' })}>
        success
      </Button>
      <Button
        variant="secondary"
        onClick={() =>
          toast({
            title: 'エラー',
            description: 'サーバ通信に失敗しました',
            variant: 'error',
          })
        }
      >
        error
      </Button>
      <Button
        variant="ghost"
        onClick={() => toast({ title: '更新を反映しました', variant: 'default' })}
      >
        default
      </Button>
      <Button
        variant="secondary"
        onClick={() =>
          toast({
            title: '永続 toast',
            description: '× ボタンで閉じるまで残ります',
            durationMs: 0,
          })
        }
      >
        永続(durationMs=0)
      </Button>
    </div>
  );
}

export const Variants: Story = {
  render: () => <ToastDemo />,
};
