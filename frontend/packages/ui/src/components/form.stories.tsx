import { zodResolver } from '@hookform/resolvers/zod';
import type { Meta, StoryObj } from '@storybook/react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';

import { Form, FormField, SubmitButton } from './form.js';

const schema = z.object({
  skuCode: z.string().min(1, 'SKU は必須です').max(20, '20 文字以内で入力してください'),
});

type Values = z.infer<typeof schema>;

interface DemoFormProps {
  /** true なら 1.5 秒待ってから submit を解決(SubmitButton の pending 表示確認用) */
  slowSubmit?: boolean;
}

/**
 * Form が generic component で Meta の argTypes 推論に向かないため、
 * 非 generic な wrapper で stories を組む。
 */
function DemoForm({ slowSubmit = false }: DemoFormProps) {
  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: { skuCode: '' },
  });
  const handleSubmit = async ({ skuCode }: Values) => {
    if (slowSubmit) await new Promise((r) => setTimeout(r, 1500));
    window.alert(`submitted: ${skuCode}`);
  };
  return (
    <div className="w-96 p-6">
      <Form form={form} onSubmit={handleSubmit}>
        <FormField<Values, 'skuCode'>
          name="skuCode"
          label="SKU コード"
          description="例: SKU-0001"
          render={({ field }) => (
            <input
              id="skuCode"
              type="text"
              className="w-full rounded-md border border-border bg-background px-3 py-1 text-sm"
              {...field}
            />
          )}
        />
        <SubmitButton label="保存" pendingLabel="保存中..." />
      </Form>
    </div>
  );
}

const meta = {
  title: 'Components/Form',
  component: DemoForm,
  tags: ['autodocs'],
} satisfies Meta<typeof DemoForm>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Sync: Story = {
  args: {},
};

export const AsyncWithPending: Story = {
  args: { slowSubmit: true },
};
