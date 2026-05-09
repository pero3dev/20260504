import type { ButtonHTMLAttributes, ReactNode } from 'react';
import {
  Controller,
  FormProvider,
  useFormContext,
  useFormState,
  type ControllerProps,
  type FieldPath,
  type FieldValues,
  type UseFormReturn,
} from 'react-hook-form';

import { cn } from '../lib/cn.js';

/**
 * react-hook-form ベースの最小 Form wrapper(ADR-0022 phase 1)。
 *
 * <p>使用パターン:
 *
 * <pre>{@code
 * const schema = z.object({ skuCode: z.string().min(1, '必須') });
 * type Values = z.infer<typeof schema>;
 * const form = useForm<Values>({ resolver: zodResolver(schema), defaultValues: { skuCode: '' } });
 * <Form form={form} onSubmit={(v) => mutate(v)}>
 *   <FormField<Values, 'skuCode'>
 *     name="skuCode"
 *     label="SKU"
 *     render={({ field }) => <input id="skuCode" className="..." {...field} />}
 *   />
 *   <button type="submit" disabled={form.formState.isSubmitting}>送信</button>
 * </Form>
 * }</pre>
 */
export interface FormProps<TValues extends FieldValues> {
  form: UseFormReturn<TValues>;
  onSubmit: (values: TValues) => void | Promise<void>;
  children: ReactNode;
  className?: string;
}

export function Form<TValues extends FieldValues>({
  form,
  onSubmit,
  children,
  className,
}: FormProps<TValues>) {
  return (
    <FormProvider {...form}>
      <form
        onSubmit={form.handleSubmit(onSubmit)}
        className={cn('space-y-4', className)}
        noValidate
      >
        {children}
      </form>
    </FormProvider>
  );
}

export interface FormFieldProps<
  TValues extends FieldValues,
  TName extends FieldPath<TValues>,
> {
  name: TName;
  label?: string;
  description?: string;
  /** react-hook-form の Controller render(field を input/select 等に bind する) */
  render: ControllerProps<TValues, TName>['render'];
}

/**
 * label + Controller + description / error message を 1 セットに束ねる field。
 *
 * <p>error は `formState.errors[name].message` を `<p role="alert">` で表示し、
 * a11y 観点で screen reader が更新を読み上げる。 description と排他(error 優先)。
 */
export function FormField<
  TValues extends FieldValues,
  TName extends FieldPath<TValues>,
>({ name, label, description, render }: FormFieldProps<TValues, TName>) {
  const {
    control,
    formState: { errors },
  } = useFormContext<TValues>();
  const errorEntry = errors[name];
  const errorMessage =
    errorEntry && typeof errorEntry === 'object' && 'message' in errorEntry
      ? String((errorEntry as { message: unknown }).message ?? '')
      : null;

  return (
    <div className="space-y-1">
      {label && (
        <label htmlFor={name} className="block text-sm font-medium">
          {label}
        </label>
      )}
      <Controller name={name} control={control} render={render} />
      {description && !errorMessage && (
        <p className="text-xs text-muted-foreground">{description}</p>
      )}
      {errorMessage && (
        <p role="alert" className="text-xs text-destructive">
          {errorMessage}
        </p>
      )}
    </div>
  );
}

export interface SubmitButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  /** 通常時のラベル(例: 「取得」)。 children でも可だが label を渡すと pending 表示と排他制御しやすい */
  label?: ReactNode;
  /** 送信中に差し替えるラベル(例: 「取得中...」)。 未指定なら label/children のまま disable のみ */
  pendingLabel?: ReactNode;
  /**
   * 外部 pending(query refetch 中など)を OR で merge するための override。
   * react-hook-form の `formState.isSubmitting` だけでは捕捉できない、 後段の useQuery
   * fetch を待ちたいケース(submit → setState → useQuery 再 fetch)で `isLoading` を渡す。
   */
  pending?: boolean;
  children?: ReactNode;
}

/**
 * `react-hook-form` の `formState.isSubmitting` を購読する submit button。
 * `<Form>` 内に置かれている前提で、 送信中は disabled + pendingLabel に切替。
 *
 * <p>使用例:
 * <pre>{@code
 * <SubmitButton label={t('dashboard.filter.fetch_button')}
 *               pendingLabel={t('dashboard.filter.fetch_button_pending')}
 *               pending={isLoading} />
 * }</pre>
 *
 * <p>外部要因で disabled したい場合は `disabled` prop を併用(OR で評価)。 isSubmitting は
 * useFormState で subscribe するため、 親 component の他の field 変更で再 render されない。
 */
export function SubmitButton({
  label,
  pendingLabel,
  pending,
  children,
  className,
  disabled,
  type = 'submit',
  ...rest
}: SubmitButtonProps) {
  const { isSubmitting } = useFormState();
  const isPending = isSubmitting || Boolean(pending);
  const display = isPending && pendingLabel !== undefined ? pendingLabel : (label ?? children);
  return (
    <button
      type={type}
      disabled={disabled || isPending}
      aria-busy={isPending || undefined}
      className={cn(
        'inline-flex items-center justify-center rounded-md bg-primary px-4 py-1 text-sm text-primary-foreground transition-opacity hover:opacity-90 disabled:pointer-events-none disabled:opacity-50',
        className,
      )}
      {...rest}
    >
      {display}
    </button>
  );
}
