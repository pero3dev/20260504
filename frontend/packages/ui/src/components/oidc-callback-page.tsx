import { useEffect, useState } from 'react';

export interface OidcCallbackManager {
  handleCallback(): Promise<void>;
}

export interface OidcCallbackPageProps {
  authManager: OidcCallbackManager;
  /** callback 成功後に遷移する callback。 通常は `() => navigate({ to: '/', replace: true })` */
  onSuccess: () => void;
}

/**
 * OIDC `redirect_uri` で表示する page。 マウント時に handleCallback() を 1 回実行し、
 * 成功なら onSuccess を呼ぶ。 失敗なら error message を表示。
 */
export function OidcCallbackPage({ authManager, onSuccess }: OidcCallbackPageProps) {
  const [error, setError] = useState<string | null>(null);
  useEffect(() => {
    authManager
      .handleCallback()
      .then(onSuccess)
      .catch((err: unknown) => setError(err instanceof Error ? err.message : String(err)));
  }, [authManager, onSuccess]);

  if (error) {
    return (
      <p className="rounded-lg border border-border bg-muted p-4 text-sm text-muted-foreground">
        OIDC callback 処理に失敗しました: {error}
      </p>
    );
  }
  return <p className="text-muted-foreground">サインイン処理中...</p>;
}
