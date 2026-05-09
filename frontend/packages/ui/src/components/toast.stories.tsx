import type { Meta, StoryObj } from '@storybook/react';

import { Button } from './button.js';
import { ToastProvider, useToast } from './toast.js';

/**
 * `useToast` は ToastProvider 配下からのみ呼べるため、 meta.decorators で全 stories を
 * Provider でラップする。 Provider 自体を component に置くと children 必須エラーが
 * 出るので、 発火デモ wrapper を component に据えて Storybook の args 推論を成立させる。
 */
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

// `satisfies` だと decorators の inferred 型が Storybook 内部 csf を参照して
// TS2742 で落ちるため explicit annotation。 LineChart story と同パターン。
const meta: Meta<typeof ToastDemo> = {
  title: 'Components/Toast',
  component: ToastDemo,
  tags: ['autodocs'],
  decorators: [
    (Story) => (
      <ToastProvider>
        <Story />
      </ToastProvider>
    ),
  ],
};

export default meta;
type Story = StoryObj<typeof ToastDemo>;

export const Variants: Story = {};
