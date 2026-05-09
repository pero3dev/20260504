import * as DialogPrimitive from '@radix-ui/react-dialog';
import { forwardRef, type ComponentPropsWithoutRef, type ElementRef, type ReactNode } from 'react';

import { cn } from '../lib/cn.js';

/**
 * shadcn 風 Dialog wrapper。 `@radix-ui/react-dialog` が focus trap / ESC close /
 * `aria-modal` / outside-click close を担保する。 本 wrapper は overlay + content の
 * Tailwind スタイルと shadcn 標準の compound API(Root / Trigger / Content / Title / ...)
 * を export する。
 *
 * <p>使用例:
 * <pre>{@code
 * <Dialog>
 *   <DialogTrigger asChild>
 *     <Button>SKU を追加</Button>
 *   </DialogTrigger>
 *   <DialogContent>
 *     <DialogTitle>SKU を追加</DialogTitle>
 *     <DialogDescription>新しい SKU を登録します。</DialogDescription>
 *     <Form ...>
 *       ...
 *       <DialogFooter>
 *         <DialogClose asChild>
 *           <Button variant="ghost">キャンセル</Button>
 *         </DialogClose>
 *         <SubmitButton label="保存" />
 *       </DialogFooter>
 *     </Form>
 *   </DialogContent>
 * </Dialog>
 * }</pre>
 *
 * <p>Radix が `<DialogPortal>` で body 直下に出すので、 親の overflow / z-index の影響を受けない。
 */
export const Dialog = DialogPrimitive.Root;
export const DialogTrigger = DialogPrimitive.Trigger;
export const DialogPortal = DialogPrimitive.Portal;
export const DialogClose = DialogPrimitive.Close;

export const DialogOverlay = forwardRef<
  ElementRef<typeof DialogPrimitive.Overlay>,
  ComponentPropsWithoutRef<typeof DialogPrimitive.Overlay>
>(function DialogOverlay({ className, ...props }, ref) {
  return (
    <DialogPrimitive.Overlay
      ref={ref}
      className={cn('fixed inset-0 z-50 bg-black/50', className)}
      {...props}
    />
  );
});

export const DialogContent = forwardRef<
  ElementRef<typeof DialogPrimitive.Content>,
  ComponentPropsWithoutRef<typeof DialogPrimitive.Content>
>(function DialogContent({ className, children, ...props }, ref) {
  return (
    <DialogPortal>
      <DialogOverlay />
      <DialogPrimitive.Content
        ref={ref}
        className={cn(
          'fixed left-1/2 top-1/2 z-50 w-full max-w-lg -translate-x-1/2 -translate-y-1/2 rounded-lg border border-border bg-background p-6 shadow-lg',
          className,
        )}
        {...props}
      >
        {children}
      </DialogPrimitive.Content>
    </DialogPortal>
  );
});

export const DialogTitle = forwardRef<
  ElementRef<typeof DialogPrimitive.Title>,
  ComponentPropsWithoutRef<typeof DialogPrimitive.Title>
>(function DialogTitle({ className, ...props }, ref) {
  return (
    <DialogPrimitive.Title
      ref={ref}
      className={cn('text-lg font-semibold leading-none', className)}
      {...props}
    />
  );
});

export const DialogDescription = forwardRef<
  ElementRef<typeof DialogPrimitive.Description>,
  ComponentPropsWithoutRef<typeof DialogPrimitive.Description>
>(function DialogDescription({ className, ...props }, ref) {
  return (
    <DialogPrimitive.Description
      ref={ref}
      className={cn('mt-2 text-sm text-muted-foreground', className)}
      {...props}
    />
  );
});

export interface DialogFooterProps {
  children: ReactNode;
  className?: string;
}

export function DialogFooter({ children, className }: DialogFooterProps) {
  return (
    <div className={cn('mt-6 flex justify-end gap-2', className)}>{children}</div>
  );
}
