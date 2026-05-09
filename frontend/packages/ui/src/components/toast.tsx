import * as ToastPrimitive from '@radix-ui/react-toast';
import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react';

import { cn } from '../lib/cn.js';

/**
 * shadcn 風 Toast。 Radix の `@radix-ui/react-toast` を背後に置き:
 *
 * <ul>
 *   <li>`<ToastProvider>` を app root に 1 つ mount すれば、 どこからでも `useToast()` で発火可能
 *   <li>auto dismiss(default 5 秒)+ ESC / swipe で手動 dismiss
 *   <li>`variant`(`default` / `success` / `error`)で色分け、 a11y は `role=status` を Radix が付与
 * </ul>
 *
 * <p>使用例:
 * <pre>{@code
 * // app root
 * <ToastProvider>
 *   <App />
 * </ToastProvider>
 *
 * // どこからでも
 * const { toast } = useToast();
 * toast({ title: '保存しました', variant: 'success' });
 * toast({ title: 'エラー', description: 'サーバ通信に失敗', variant: 'error', durationMs: 8000 });
 * }</pre>
 */
export type ToastVariant = 'default' | 'success' | 'error';

export interface ToastOptions {
  title: ReactNode;
  description?: ReactNode;
  variant?: ToastVariant;
  /** 自動消滅までの ms(default 5000)。 0 を渡すと永続(× 押すまで残る) */
  durationMs?: number;
}

interface ActiveToast extends ToastOptions {
  id: string;
}

interface ToastContextValue {
  toast: (options: ToastOptions) => void;
  dismiss: (id: string) => void;
}

const ToastContext = createContext<ToastContextValue | null>(null);

export interface ToastProviderProps {
  children: ReactNode;
  /** Viewport の swipe 方向(default `right`、 LTR 想定) */
  swipeDirection?: 'right' | 'left' | 'up' | 'down';
}

const variantClasses: Record<ToastVariant, string> = {
  default: 'border-border bg-background text-foreground',
  success: 'border-emerald-500/40 bg-emerald-50 text-emerald-900',
  error: 'border-destructive/40 bg-destructive/10 text-destructive',
};

export function ToastProvider({ children, swipeDirection = 'right' }: ToastProviderProps) {
  const [items, setItems] = useState<ActiveToast[]>([]);
  const counterRef = useRef(0);

  const dismiss = useCallback((id: string) => {
    setItems((prev) => prev.filter((t) => t.id !== id));
  }, []);

  const toast = useCallback((options: ToastOptions) => {
    counterRef.current += 1;
    const id = `t${counterRef.current}-${Date.now()}`;
    setItems((prev) => [...prev, { ...options, id }]);
  }, []);

  const value = useMemo<ToastContextValue>(() => ({ toast, dismiss }), [toast, dismiss]);

  return (
    <ToastContext.Provider value={value}>
      <ToastPrimitive.Provider swipeDirection={swipeDirection} duration={5000}>
        {children}
        {items.map((item) => (
          <ToastPrimitive.Root
            key={item.id}
            // exactOptionalPropertyTypes 適合のため undefined は渡さず spread で条件付与。
            // 未指定時は Provider の duration={5000} を継承する。
            {...(item.durationMs !== undefined ? { duration: item.durationMs } : {})}
            onOpenChange={(open) => {
              if (!open) dismiss(item.id);
            }}
            className={cn(
              'pointer-events-auto flex w-80 items-start gap-3 rounded-lg border p-3 shadow-md',
              variantClasses[item.variant ?? 'default'],
            )}
          >
            <div className="flex-1 space-y-0.5">
              <ToastPrimitive.Title className="text-sm font-semibold">
                {item.title}
              </ToastPrimitive.Title>
              {item.description && (
                <ToastPrimitive.Description className="text-xs opacity-90">
                  {item.description}
                </ToastPrimitive.Description>
              )}
            </div>
            <ToastPrimitive.Close
              aria-label="閉じる"
              className="rounded p-1 text-xs opacity-60 hover:opacity-100"
            >
              ×
            </ToastPrimitive.Close>
          </ToastPrimitive.Root>
        ))}
        <ToastPrimitive.Viewport
          className={cn(
            'pointer-events-none fixed bottom-4 right-4 z-50 flex w-96 max-w-full flex-col gap-2',
          )}
        />
      </ToastPrimitive.Provider>
    </ToastContext.Provider>
  );
}

/**
 * `<ToastProvider>` 配下からのみ呼べる。 外で呼ぶとエラー(誤配置を早期発見)。
 */
export function useToast(): ToastContextValue {
  const ctx = useContext(ToastContext);
  if (!ctx) {
    throw new Error(
      'useToast must be used within <ToastProvider>. Mount it once near the app root.',
    );
  }
  return ctx;
}
