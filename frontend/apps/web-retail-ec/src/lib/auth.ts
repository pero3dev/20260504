import {
  createAuthManager,
  readOidcConfigFromEnv,
  type AuthManager,
} from '@inventory/shared/web-auth';

/**
 * web-retail-ec 用の AuthManager 単一 instance(F2 phase B)。
 *
 * <p>VITE_OIDC_AUTHORITY / VITE_OIDC_CLIENT_ID / VITE_OIDC_REDIRECT_URI が揃って
 * いれば oidc-client-ts UserManager を、 そうでなければ dev fallback(sessionStorage に
 * dummy token を入れるだけ)を使う。
 */
export const authManager: AuthManager = createAuthManager(
  readOidcConfigFromEnv(import.meta.env as unknown as Record<string, unknown>),
  'web-retail-ec-auth',
);

export function getAuthToken(): string | null {
  return authManager.getAccessToken();
}
