// F1 stub: 単純な localStorage 経由の dev token。 F2 で oidc-client-ts ベースに差替え。
//
// 流れ(F2 想定):
//   1. UserManager(authority = Identity Broker、 client_id = retail-ec-web)で Cognito へリダイレクト
//   2. callback で id_token + access_token を取得
//   3. Identity Broker の `/v1/auth/tenant-sessions` に session token を渡し tenant-scoped JWT を交換
//   4. 取得した JWT を本 helper で取り出す

const KEY = 'retail-ec-dev-token';

export function getAuthToken(): string | null {
  if (typeof window === 'undefined') return null;
  return window.localStorage.getItem(KEY);
}

export function setAuthToken(token: string): void {
  if (typeof window === 'undefined') return;
  window.localStorage.setItem(KEY, token);
}

export function clearAuthToken(): void {
  if (typeof window === 'undefined') return;
  window.localStorage.removeItem(KEY);
}
