import { UserManager } from 'oidc-client-ts';

import { readOidcConfigFromEnv } from './auth-manager.js';

/**
 * `silent-renew.html` から呼ばれる callback ハンドラ(F2 残)。
 *
 * <p>oidc-client-ts の `automaticSilentRenew=true` は token expire 60 秒前に
 * hidden iframe 経由 `silent_redirect_uri` を読み込み、 認可 server から code を
 * 受け取って parent window の UserManager に postMessage で返す。 本 helper は
 * その iframe 側で UserManager の `signinSilentCallback()` を呼んで code 抽出 +
 * postMessage を実行する。
 *
 * <p>iframe は新しい document context なので parent と UserManager 状態を共有しない。
 * したがって同じ env から OIDC 設定を読み直して UserManager を構築する。 認可 cookie
 * は同 origin であれば iframe にも届くので silent renew が成立する。
 *
 * <p>OIDC 設定が無い(dev fallback)場合は no-op。 prod でも誤動作しない。
 *
 * @param env Vite の `import.meta.env`(各 web app の silent-renew entry から渡す)
 */
export async function runSilentRenewCallback(
  env: Record<string, unknown>,
): Promise<void> {
  const config = readOidcConfigFromEnv(env);
  if (!config) return;
  const um = new UserManager({
    authority: config.authority,
    client_id: config.clientId,
    redirect_uri: config.redirectUri,
    scope: config.scope ?? 'openid profile',
    monitorSession: false,
  });
  try {
    await um.signinSilentCallback();
  } catch (err) {
    // iframe 内で例外を上に伝播させても誰も拾えないので、 console に残して
    // parent 側 UserManager の `silentRenewError` event(auth-manager.ts で warn)に任せる。
    console.warn('OIDC silent renew callback failed:', err);
  }
}
