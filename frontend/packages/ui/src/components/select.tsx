import * as SelectPrimitive from '@radix-ui/react-select';
import { forwardRef, type ComponentPropsWithoutRef, type ElementRef, type ReactNode } from 'react';

import { cn } from '../lib/cn.js';

/**
 * shadcn 風 Select wrapper(`@radix-ui/react-select` 背後)。 native `<select>` ではなく
 * カスタム dropdown だが、 Radix が ARIA listbox / 矢印キー / type-ahead / scroll を担保するので
 * a11y に対応しつつ Tailwind で見た目を design system 内に揃えられる。
 *
 * <p>使用例:
 * <pre>{@code
 * <Select value={status} onValueChange={setStatus}>
 *   <SelectTrigger aria-label="ステータス">
 *     <SelectValue placeholder="選択..." />
 *   </SelectTrigger>
 *   <SelectContent>
 *     <SelectItem value="PLANNED">計画中</SelectItem>
 *     <SelectItem value="RELEASED">手配済</SelectItem>
 *     <SelectItem value="COMPLETED">完了</SelectItem>
 *   </SelectContent>
 * </Select>
 * }</pre>
 *
 * <p>選択値は文字列のみ(Radix 仕様)。 Saga state など number / enum を渡す場合は
 * 呼び出し側で `String(value)` ↔ enum/number 変換する。
 */
export const Select = SelectPrimitive.Root;
export const SelectGroup = SelectPrimitive.Group;
export const SelectValue = SelectPrimitive.Value;

export const SelectTrigger = forwardRef<
  ElementRef<typeof SelectPrimitive.Trigger>,
  ComponentPropsWithoutRef<typeof SelectPrimitive.Trigger>
>(function SelectTrigger({ className, children, ...props }, ref) {
  return (
    <SelectPrimitive.Trigger
      ref={ref}
      className={cn(
        'inline-flex h-9 w-full items-center justify-between rounded-md border border-border bg-background px-3 py-1 text-sm transition-opacity hover:opacity-90 disabled:pointer-events-none disabled:opacity-50',
        className,
      )}
      {...props}
    >
      {children}
      <SelectPrimitive.Icon asChild>
        <span className="ml-2 text-xs opacity-60" aria-hidden>
          ▼
        </span>
      </SelectPrimitive.Icon>
    </SelectPrimitive.Trigger>
  );
});

export const SelectContent = forwardRef<
  ElementRef<typeof SelectPrimitive.Content>,
  ComponentPropsWithoutRef<typeof SelectPrimitive.Content>
>(function SelectContent({ className, children, position = 'popper', ...props }, ref) {
  return (
    <SelectPrimitive.Portal>
      <SelectPrimitive.Content
        ref={ref}
        position={position}
        className={cn(
          'z-50 min-w-[var(--radix-select-trigger-width)] overflow-hidden rounded-md border border-border bg-background text-foreground shadow-md',
          'data-[state=open]:animate-in data-[state=open]:fade-in',
          'data-[state=closed]:animate-out data-[state=closed]:fade-out',
          className,
        )}
        {...props}
      >
        <SelectPrimitive.Viewport className="p-1">{children}</SelectPrimitive.Viewport>
      </SelectPrimitive.Content>
    </SelectPrimitive.Portal>
  );
});

export const SelectLabel = forwardRef<
  ElementRef<typeof SelectPrimitive.Label>,
  ComponentPropsWithoutRef<typeof SelectPrimitive.Label>
>(function SelectLabel({ className, ...props }, ref) {
  return (
    <SelectPrimitive.Label
      ref={ref}
      className={cn('px-2 py-1 text-xs font-semibold text-muted-foreground', className)}
      {...props}
    />
  );
});

export interface SelectItemProps
  extends ComponentPropsWithoutRef<typeof SelectPrimitive.Item> {
  children: ReactNode;
}

export const SelectItem = forwardRef<
  ElementRef<typeof SelectPrimitive.Item>,
  SelectItemProps
>(function SelectItem({ className, children, ...props }, ref) {
  return (
    <SelectPrimitive.Item
      ref={ref}
      className={cn(
        'relative flex w-full cursor-default select-none items-center rounded-sm py-1.5 pl-7 pr-2 text-sm outline-none data-[highlighted]:bg-muted data-[disabled]:pointer-events-none data-[disabled]:opacity-50',
        className,
      )}
      {...props}
    >
      <span className="absolute left-2 inline-flex items-center" aria-hidden>
        <SelectPrimitive.ItemIndicator>✓</SelectPrimitive.ItemIndicator>
      </span>
      <SelectPrimitive.ItemText>{children}</SelectPrimitive.ItemText>
    </SelectPrimitive.Item>
  );
});

export const SelectSeparator = forwardRef<
  ElementRef<typeof SelectPrimitive.Separator>,
  ComponentPropsWithoutRef<typeof SelectPrimitive.Separator>
>(function SelectSeparator({ className, ...props }, ref) {
  return (
    <SelectPrimitive.Separator
      ref={ref}
      className={cn('-mx-1 my-1 h-px bg-border', className)}
      {...props}
    />
  );
});
