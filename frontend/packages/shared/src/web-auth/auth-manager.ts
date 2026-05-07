import { UserManager, type UserManagerSettings } from 'oidc-client-ts';

/**
 * web app が見る共通の auth interface(F2 phase B)。
 *
 * <p>Identity Broker (Cognito 経由) と stub dev mode の両方を背後に隠す。
 * 各 web app は本 interface を介して token を取り login / logout を起動する。
 */
export interface AuthManager {
  /** 現在保持している access token。 未認証なら null。 同期取得 */
  getAccessToken(): string | null;
  /** 認証済みかどうかの軽量チェック(token の expire は含まない) */
  isAuthenticated(): boolean;
  /** Login を起動。 OIDC は authority へ redirect。 dev は in-memory token を即座に発行 */
  signIn(): Promise<void>;
  /** Logout。 OIDC は end-session endpoint へ redirect。 dev は単に token を消す */
  signOut(): Promise<void>;
  /** OIDC `redirect_uri` で実行。 token を取り出して保存。 dev mode では no-op */
  handleCallback(): Promise<void>;
}

/**
 * OIDC 設定(`VITE_OIDC_*` 環境変数から構築)。 全項目が揃わない場合は dev fallback。
 */
export interface OidcConfig {
  /** Identity Broker (or Cognito User Pool) の OIDC issuer URL(末尾 `/` 不問) */
  authority: string;
  /** Identity Broker に登録済みのクライアント ID */
  clientId: string;
  /** OAuth callback URL(本 app が hosting する `/callback` 等) */
  redirectUri: string;
  /** Logout 後の遷移先(省略時は redirectUri と同じ origin) */
  postLogoutRedirectUri?: string;
  /**
   * Silent renew 用 hidden iframe redirect URL(`/silent-renew` 等)。 prod は本 URL に
   * 「postMessage で UserManager に code を返すだけの空ページ」を hosting する想定
   * (Vite なら public/silent-renew.html)。 省略時は signin 用 redirect_uri を再利用するが、
   * iframe 内で React app が立ち上がってしまうため非推奨。
   */
  silentRedirectUri?: string;
  /** 取得するスコープ(`openid profile` 等)。 default は `openid profile` */
  scope?: string;
}

/**
 * dev fallback 用の AuthManager 実装。 sessionStorage に token を保存し、
 * `signIn()` で fixed dummy token を発行する。 prod 想定 OIDC 設定が無い時のみ使う。
 */
class DevAuthManager implements AuthManager {
  private readonly storageKey: string;

  constructor(storageKey: string) {
    this.storageKey = storageKey;
  }

  getAccessToken(): string | null {
    if (typeof window === 'undefined') return null;
    return window.sessionStorage.getItem(this.storageKey);
  }

  isAuthenticated(): boolean {
    return this.getAccessToken() !== null;
  }

  async signIn(): Promise<void> {
    if (typeof window === 'undefined') return;
    // dev token: 「これは dev」と分かる prefix。 BFF verifier 設定済の prod では署名検証で reject される
    window.sessionStorage.setItem(this.storageKey, `dev-token-${Date.now()}`);
  }

  async signOut(): Promise<void> {
    if (typeof window === 'undefined') return;
    window.sessionStorage.removeItem(this.storageKey);
  }

  async handleCallback(): Promise<void> {
    // dev mode は redirect 経由 login を持たないので no-op
  }
}

/**
 * oidc-client-ts UserManager を AuthManager に適合させる実装。 `User` は
 * UserManager 内蔵の sessionStorage に持たせ、 cross-tab 同期は OIDC の
 * `monitorSession` に任せる(本 phase ではあえて使わない)。
 *
 * <p>silent token refresh: `automaticSilentRenew=true` で UserManager が
 * `accessTokenExpiring` 時に hidden iframe 経由 (`/silent-renew`) で refresh する。
 * refresh 失敗時は `silentRenewError` event が発火するので signOut を caller に通知。
 */
class OidcAuthManager implements AuthManager {
  private readonly um: UserManager;
  private cachedAccessToken: string | null = null;

  constructor(settings: UserManagerSettings) {
    this.um = new UserManager(settings);
    // 既に session 内に token が居る場合、 同期 getAccessToken に応えるため warmup
    void this.um.getUser().then((u) => {
      this.cachedAccessToken = u?.access_token ?? null;
    });
    // silent renew 成功 → cache 更新
    this.um.events.addUserLoaded((u) => {
      this.cachedAccessToken = u.access_token ?? null;
    });
    // session timeout / refresh 失敗 → cache クリア(UI が再 login を促す)
    this.um.events.addUserUnloaded(() => {
      this.cachedAccessToken = null;
    });
    this.um.events.addAccessTokenExpired(() => {
      this.cachedAccessToken = null;
    });
    this.um.events.addSilentRenewError((err) => {
      // eslint-disable-next-line no-console
      console.warn('OIDC silent renew failed:', err);
      this.cachedAccessToken = null;
    });
  }

  getAccessToken(): string | null {
    return this.cachedAccessToken;
  }

  isAuthenticated(): boolean {
    return this.cachedAccessToken !== null;
  }

  async signIn(): Promise<void> {
    await this.um.signinRedirect();
  }

  async signOut(): Promise<void> {
    this.cachedAccessToken = null;
    await this.um.signoutRedirect();
  }

  async handleCallback(): Promise<void> {
    const user = await this.um.signinRedirectCallback();
    this.cachedAccessToken = user.access_token ?? null;
  }
}

/**
 * env 設定の有無を見て、 OIDC か dev fallback の AuthManager を返す factory。
 *
 * @param config OIDC 設定。 `null` の場合は dev fallback
 * @param devStorageKey dev fallback 用 sessionStorage key(app 単位で衝突しないよう一意化)
 */
export function createAuthManager(
  config: OidcConfig | null,
  devStorageKey: string,
): AuthManager {
  if (!config) {
    return new DevAuthManager(devStorageKey);
  }
  return new OidcAuthManager({
    authority: config.authority,
    client_id: config.clientId,
    redirect_uri: config.redirectUri,
    response_type: 'code',
    scope: config.scope ?? 'openid profile',
    automaticSilentRenew: true,
    // accessTokenExpiringNotificationTimeInSeconds の default 60s で、 expire 60秒前に
    // accessTokenExpiring が発火 → automaticSilentRenew=true なら自動で signinSilent を起動
    monitorSession: false,
    ...(config.postLogoutRedirectUri !== undefined
      ? { post_logout_redirect_uri: config.postLogoutRedirectUri }
      : {}),
    ...(config.silentRedirectUri !== undefined
      ? { silent_redirect_uri: config.silentRedirectUri }
      : {}),
  });
}

/**
 * Vite の `import.meta.env` から OIDC 設定を組み立てる。 全項目が揃っていない場合は null を返す。
 *
 * @param env `import.meta.env`(各 web app から渡す。 unit test は手動 stub)
 */
export function readOidcConfigFromEnv(env: Record<string, unknown>): OidcConfig | null {
  const authority = pickString(env['VITE_OIDC_AUTHORITY']);
  const clientId = pickString(env['VITE_OIDC_CLIENT_ID']);
  const redirectUri = pickString(env['VITE_OIDC_REDIRECT_URI']);
  if (!authority || !clientId || !redirectUri) return null;

  const config: OidcConfig = { authority, clientId, redirectUri };
  const post = pickString(env['VITE_OIDC_POST_LOGOUT_REDIRECT_URI']);
  if (post) config.postLogoutRedirectUri = post;
  const silent = pickString(env['VITE_OIDC_SILENT_REDIRECT_URI']);
  if (silent) config.silentRedirectUri = silent;
  const scope = pickString(env['VITE_OIDC_SCOPE']);
  if (scope) config.scope = scope;
  return config;
}

function pickString(value: unknown): string | undefined {
  return typeof value === 'string' && value.length > 0 ? value : undefined;
}
