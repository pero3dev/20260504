import type { FallbackProps } from 'react-error-boundary';

/**
 * `react-error-boundary` の `<ErrorBoundary fallbackRender={...}>` または
 * `FallbackComponent` に渡す既定実装(ADR-0022 phase 1)。
 *
 * <p>各 web app で同 UI が要らない場合のみ独自 fallback を渡す。 ボタンは
 * `resetErrorBoundary()` を呼び出し、 親 boundary が再 mount される。
 *
 * <p>RUM(Datadog 等)送信は `<ErrorBoundary onError={...}>` 側に書く想定。
 * 本 component は表示のみ。
 */
export function DefaultErrorFallback({ error, resetErrorBoundary }: FallbackProps) {
  const message = error instanceof Error ? error.message : String(error);
  return (
    <div
      role="alert"
      aria-live="assertive"
      className="space-y-3 rounded-lg border border-destructive/40 bg-muted p-6"
    >
      <h2 className="text-lg font-semibold">予期しないエラーが発生しました</h2>
      <pre className="overflow-auto text-xs text-muted-foreground">{message}</pre>
      <button
        type="button"
        onClick={resetErrorBoundary}
        className="rounded-md border border-border bg-background px-3 py-1 text-sm hover:bg-muted"
      >
        再試行
      </button>
    </div>
  );
}
