import { useEffect, useState } from 'react';

/**
 * `@inventory/shared/web-auth` の AuthManager に依存しない最小 interface。
 * UI は `@inventory/ui` 単独で typecheck できるよう、 ここで再宣言する。
 */
export interface AuthButtonsManager {
  isAuthenticated(): boolean;
  signIn(): Promise<void>;
  signOut(): Promise<void>;
}

export interface AuthButtonsProps {
  authManager: AuthButtonsManager;
  /** 認証状態の polling 間隔(ms)。 default 1000 */
  pollIntervalMs?: number;
}

/**
 * Login / Logout 切替 button 単体 component。 4 web app の RootLayout
 * で同パターンを書かせない。 prod の OIDC では signIn → full page redirect が走るので
 * polling は dev fallback の signIn 直後に UI を再描画する用途。
 */
export function AuthButtons({ authManager, pollIntervalMs = 1000 }: AuthButtonsProps) {
  const [authed, setAuthed] = useState(authManager.isAuthenticated());
  useEffect(() => {
    const id = setInterval(() => setAuthed(authManager.isAuthenticated()), pollIntervalMs);
    return () => clearInterval(id);
  }, [authManager, pollIntervalMs]);

  return authed ? (
    <button
      type="button"
      onClick={() => void authManager.signOut()}
      className="rounded-md border border-border bg-background px-3 py-1 text-sm hover:bg-muted"
    >
      Logout
    </button>
  ) : (
    <button
      type="button"
      onClick={() => void authManager.signIn()}
      className="rounded-md bg-primary px-3 py-1 text-sm text-primary-foreground hover:opacity-90"
    >
      Login
    </button>
  );
}
