import { Command as CommandPrimitive } from 'cmdk';
import { forwardRef, type ComponentPropsWithoutRef, type ElementRef } from 'react';

import { cn } from '../lib/cn.js';

/**
 * shadcn 風 Command(cmdk wrapper)。 keyboard nav / fuzzy 部分一致 / 仮想スクロール対応の
 * search palette を提供する。 主に `<Combobox>` の中身として使うが、 単体でも検索 UI に使える。
 *
 * <p>使用例:
 * <pre>{@code
 * <Command>
 *   <CommandInput placeholder="SKU を検索..." />
 *   <CommandList>
 *     <CommandEmpty>該当なし</CommandEmpty>
 *     <CommandGroup heading="人気の SKU">
 *       <CommandItem onSelect={() => choose('SKU-0001')}>SKU-0001</CommandItem>
 *       <CommandItem onSelect={() => choose('SKU-0002')}>SKU-0002</CommandItem>
 *     </CommandGroup>
 *   </CommandList>
 * </Command>
 * }</pre>
 */
export const Command = forwardRef<
  ElementRef<typeof CommandPrimitive>,
  ComponentPropsWithoutRef<typeof CommandPrimitive>
>(function Command({ className, ...props }, ref) {
  return (
    <CommandPrimitive
      ref={ref}
      className={cn(
        'flex h-full w-full flex-col overflow-hidden rounded-md bg-background text-foreground',
        className,
      )}
      {...props}
    />
  );
});

export const CommandInput = forwardRef<
  ElementRef<typeof CommandPrimitive.Input>,
  ComponentPropsWithoutRef<typeof CommandPrimitive.Input>
>(function CommandInput({ className, ...props }, ref) {
  return (
    <div className="flex items-center border-b border-border px-3" cmdk-input-wrapper="">
      <span className="mr-2 text-muted-foreground" aria-hidden>
        🔍
      </span>
      <CommandPrimitive.Input
        ref={ref}
        className={cn(
          'flex h-9 w-full bg-transparent py-2 text-sm outline-none placeholder:text-muted-foreground disabled:cursor-not-allowed disabled:opacity-50',
          className,
        )}
        {...props}
      />
    </div>
  );
});

export const CommandList = forwardRef<
  ElementRef<typeof CommandPrimitive.List>,
  ComponentPropsWithoutRef<typeof CommandPrimitive.List>
>(function CommandList({ className, ...props }, ref) {
  return (
    <CommandPrimitive.List
      ref={ref}
      className={cn('max-h-[300px] overflow-y-auto overflow-x-hidden', className)}
      {...props}
    />
  );
});

export const CommandEmpty = forwardRef<
  ElementRef<typeof CommandPrimitive.Empty>,
  ComponentPropsWithoutRef<typeof CommandPrimitive.Empty>
>(function CommandEmpty(props, ref) {
  return (
    <CommandPrimitive.Empty
      ref={ref}
      className="py-6 text-center text-sm text-muted-foreground"
      {...props}
    />
  );
});

export const CommandGroup = forwardRef<
  ElementRef<typeof CommandPrimitive.Group>,
  ComponentPropsWithoutRef<typeof CommandPrimitive.Group>
>(function CommandGroup({ className, ...props }, ref) {
  return (
    <CommandPrimitive.Group
      ref={ref}
      className={cn(
        'overflow-hidden p-1 text-foreground [&_[cmdk-group-heading]]:px-2 [&_[cmdk-group-heading]]:py-1.5 [&_[cmdk-group-heading]]:text-xs [&_[cmdk-group-heading]]:font-medium [&_[cmdk-group-heading]]:text-muted-foreground',
        className,
      )}
      {...props}
    />
  );
});

export const CommandItem = forwardRef<
  ElementRef<typeof CommandPrimitive.Item>,
  ComponentPropsWithoutRef<typeof CommandPrimitive.Item>
>(function CommandItem({ className, ...props }, ref) {
  return (
    <CommandPrimitive.Item
      ref={ref}
      className={cn(
        'relative flex cursor-default select-none items-center rounded-sm px-2 py-1.5 text-sm outline-none data-[selected=true]:bg-muted data-[disabled=true]:pointer-events-none data-[disabled=true]:opacity-50',
        className,
      )}
      {...props}
    />
  );
});

export const CommandSeparator = forwardRef<
  ElementRef<typeof CommandPrimitive.Separator>,
  ComponentPropsWithoutRef<typeof CommandPrimitive.Separator>
>(function CommandSeparator({ className, ...props }, ref) {
  return (
    <CommandPrimitive.Separator
      ref={ref}
      className={cn('-mx-1 h-px bg-border', className)}
      {...props}
    />
  );
});
