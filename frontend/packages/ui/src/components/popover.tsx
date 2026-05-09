import * as PopoverPrimitive from '@radix-ui/react-popover';
import { forwardRef, type ComponentPropsWithoutRef, type ElementRef } from 'react';

import { cn } from '../lib/cn.js';

/**
 * shadcn 風 Popover wrapper(`@radix-ui/react-popover` 背後)。 Combobox / Tooltip / 補足情報の
 * 浮動 UI を扱う土台。 focus 管理 / outside-click 閉 / aria-* は Radix が担当。
 *
 * <p>使用例:
 * <pre>{@code
 * <Popover>
 *   <PopoverTrigger asChild>
 *     <Button variant="ghost">詳細を表示</Button>
 *   </PopoverTrigger>
 *   <PopoverContent className="w-80">
 *     <p>注文 #12345 の詳細...</p>
 *   </PopoverContent>
 * </Popover>
 * }</pre>
 */
export const Popover = PopoverPrimitive.Root;
export const PopoverTrigger = PopoverPrimitive.Trigger;
export const PopoverAnchor = PopoverPrimitive.Anchor;

export const PopoverContent = forwardRef<
  ElementRef<typeof PopoverPrimitive.Content>,
  ComponentPropsWithoutRef<typeof PopoverPrimitive.Content>
>(function PopoverContent({ className, align = 'center', sideOffset = 4, ...props }, ref) {
  return (
    <PopoverPrimitive.Portal>
      <PopoverPrimitive.Content
        ref={ref}
        align={align}
        sideOffset={sideOffset}
        className={cn(
          'z-50 w-72 rounded-md border border-border bg-background p-4 text-foreground shadow-md outline-none',
          'data-[state=open]:animate-in data-[state=open]:fade-in data-[state=open]:zoom-in-95',
          'data-[state=closed]:animate-out data-[state=closed]:fade-out data-[state=closed]:zoom-out-95',
          className,
        )}
        {...props}
      />
    </PopoverPrimitive.Portal>
  );
});
