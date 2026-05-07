import type { BffUserClaims, JwtVerifier } from './verify-jwt.js';

/**
 * BFF Apollo context で都度実行する auth 抽出 + verify。 4 業態 BFF で重複しない様に shared 集約。
 *
 * <p>戻り値:
 *
 * <ul>
 *   <li>{@code authToken} — backend に pass-through する元 JWT。 verifier 未設定 (= dev) でも通す
 *   <li>{@code user} — verify 成功時の claim 抽出結果。 verifier 未設定または token 無しなら null
 * </ul>
 *
 * verify 失敗(署名不一致・期限切れ等)は呼出側に throw が伝播し、 Apollo 側で 401 として扱える。
 */
export interface BffAuthResult {
  authToken: string | null;
  user: BffUserClaims | null;
}

export interface BuildBffAuthOptions {
  authorizationHeader: string | undefined;
  verifier: JwtVerifier | null;
}

export async function buildBffAuth(options: BuildBffAuthOptions): Promise<BffAuthResult> {
  const { authorizationHeader, verifier } = options;
  const authToken = authorizationHeader?.startsWith('Bearer ')
    ? authorizationHeader.slice('Bearer '.length)
    : null;

  if (!verifier || !authToken) {
    return { authToken, user: null };
  }
  const user = await verifier(authToken);
  return { authToken, user };
}
