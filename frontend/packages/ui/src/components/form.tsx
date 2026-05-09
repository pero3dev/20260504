import type { ReactNode } from 'react';
import {
  Controller,
  FormProvider,
  useFormContext,
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
