import type { ReactNode } from 'react';

import { cn } from '../lib/cn.js';

/**
 * Cursor-based pagination の prev/next + 状態表示(ADR の REST 規約に従い page number は持たない)。
 *
 * <p>本 component は presentational のみで cursor 値を持たず、 親が cursor / fetch を管理する:
 *
 * <pre>{@code
 * const [cursor, setCursor] = useState<string | null>(null);
 * const { data } = useQuery({ queryKey: ['list', cursor], queryFn: () => fetchList({ cursor }) });
 * <Pagination
 *   hasPrev={!!data?.prevCursor}
 *   hasNext={!!data?.nextCursor}
 *   onPrev={() => setCursor(data?.prevCursor ?? null)}
 *   onNext={() => setCursor(data?.nextCursor ?? null)}
 *   pageInfo={t('pagination.range', { start: 1, end: data?.items.length ?? 0 })}
 *   isPending={isFetching}
 * />
 * }</pre>
 *
 * <p>表示順は LTR / RTL で適切に並ぶよう CSS で flex order に任せる(brand label のような i18n
 * 影響はないので Tailwind 標準で十分)。 「前へ」「次へ」のテキストは i18n 配下の `prevLabel`
 * / `nextLabel` を必須にし、 default 文言は持たない(英語/日本語混在を防ぐため)。
 */
export interface PaginationProps {
  hasPrev: boolean;
  hasNext: boolean;
  onPrev: () => void;
  onNext: () => void;
  /** 中央に表示する page info(例: 「11 - 20 / 134」)。 i18n は呼び出し側で生成 */
  pageInfo?: ReactNode;
  /** fetch 中に prev/next を disable + aria-busy するか(default false) */
  isPending?: boolean;
  /** 「前へ」ラベル(i18n のため必須) */
  prevLabel: string;
  /** 「次へ」ラベル(i18n のため必須) */
  nextLabel: string;
  /** nav 領域全体の aria-label(i18n、 例:「ページ送り」) */
  ariaLabel: string;
  className?: string;
}

export function Pagination({
  hasPrev,
  hasNext,
  onPrev,
  onNext,
  pageInfo,
  isPending = false,
  prevLabel,
  nextLabel,
  ariaLabel,
  className,
}: PaginationProps) {
  return (
    <nav
      aria-label={ariaLabel}
      className={cn('flex items-center justify-between gap-3 text-sm', className)}
    >
      <button
        type="button"
        onClick={onPrev}
        disabled={!hasPrev || isPending}
        aria-busy={isPending || undefined}
        className="rounded-md border border-border bg-background px-3 py-1 text-foreground transition-opacity hover:opacity-80 disabled:pointer-events-none disabled:opacity-40"
      >
        {prevLabel}
      </button>
      {pageInfo !== undefined && (
        <span className="text-muted-foreground" aria-live="polite">
          {pageInfo}
        </span>
      )}
      <button
        type="button"
        onClick={onNext}
        disabled={!hasNext || isPending}
        aria-busy={isPending || undefined}
        className="rounded-md border border-border bg-background px-3 py-1 text-foreground transition-opacity hover:opacity-80 disabled:pointer-events-none disabled:opacity-40"
      >
        {nextLabel}
      </button>
    </nav>
  );
}
