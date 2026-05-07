// commons-error の RFC 7807 ProblemDetail を frontend 側で扱うための型 + 判定 helper。
// BFF / Web の両方で fetch エラー処理に使う。
export interface ProblemDetail {
  type: string;
  title: string;
  status: number;
  detail?: string;
  errorCode?: string;
  traceId?: string;
}

export function isProblemDetail(value: unknown): value is ProblemDetail {
  if (typeof value !== 'object' || value === null) return false;
  const v = value as Record<string, unknown>;
  return (
    typeof v.type === 'string' && typeof v.title === 'string' && typeof v.status === 'number'
  );
}
