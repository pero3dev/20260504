import {
  createLocalJWKSet,
  createRemoteJWKSet,
  jwtVerify,
  type JWTPayload,
  type JWTVerifyGetKey,
} from 'jose';

/**
 * BFF が verify 後に context として持つ user 情報。 Identity Broker (`NimbusJwtTokenIssuer`)
 * の access token claim と一致(`token_use=access`、 `tenant_id`、 `roles[]`、
 * `scopes.{locations,partners}`、 `mfa_strength`、 `locale`)。
 */
export interface BffUserClaims {
  /** Identity Broker の UserId(`sub` claim を Number 化) */
  userId: number;
  /** テナント識別子(`tenant_id` claim) */
  tenantId: string;
  /** Spring Security ロール名(`roles` claim) */
  roles: string[];
  /** リソーススコープ(`scopes` claim) */
  scopes: {
    locations: string[];
    partners: string[];
  };
  /** ステップアップ MFA 強度(`mfa_strength` claim、Phase 1 では low 固定) */
  mfaStrength: 'low' | 'medium' | 'high';
  /**
   * テナント運用言語(`locale` claim、 ADR-0022 phase 5a)。 web 側 `i18n.changeLanguage()`
   * の入力。 IB が未発行の token(旧 token / federated 経路前)では `ja` fallback。
   */
  locale: string;
}

export interface JwtVerifierOptions {
  /**
   * JWKS 取得関数。 prod は `createRemoteJwks(url)` を、 test は `createLocalJwks(jwks)` を渡す。
   * URL 渡しでなく関数 inject 形式にすることで、 単体テストはネットワーク無しで完結する。
   */
  jwks: JWTVerifyGetKey;
  /** 期待する `iss`(`platform.identity.issuer` と一致させる) */
  issuer: string;
  /**
   * 期待する `aud`(BFF は通常テナント単位で受信するわけではないため未指定可)。
   * 指定するとテナント横断トークン(token_use=session)の混入を防げる。
   */
  audience?: string | undefined;
  /** 許容する clock skew(秒、 default 30) */
  clockToleranceSeconds?: number | undefined;
}

export type JwtVerifier = (token: string) => Promise<BffUserClaims>;

export class JwtVerificationError extends Error {
  constructor(message: string, cause?: unknown) {
    super(message, cause === undefined ? undefined : { cause });
    this.name = 'JwtVerificationError';
  }
}

/**
 * 与えられた {@link JWTVerifyGetKey} で Identity Broker の RS256 アクセストークンを検証する
 * verifier を返す。 鍵取得方法(remote JWKS / local JWKS / 任意 callback)は呼出側責務。
 */
export function createJwtVerifier(options: JwtVerifierOptions): JwtVerifier {
  const { jwks, issuer, audience, clockToleranceSeconds = 30 } = options;
  return async (token: string): Promise<BffUserClaims> => {
    try {
      const { payload } = await jwtVerify(token, jwks, {
        issuer,
        algorithms: ['RS256'],
        clockTolerance: clockToleranceSeconds,
        ...(audience !== undefined ? { audience } : {}),
      });
      return mapClaimsOrThrow(payload);
    } catch (err) {
      if (err instanceof JwtVerificationError) throw err;
      throw new JwtVerificationError(
        `JWT verify に失敗: ${err instanceof Error ? err.message : String(err)}`,
        err,
      );
    }
  };
}

/**
 * Remote JWKS endpoint(`http(s)://.../.well-known/jwks.json`)から鍵を引く。
 * jose の `createRemoteJWKSet` は cooldown(default 30s)付きで in-process キャッシュする。
 */
export function createRemoteJwks(jwksUrl: string): JWTVerifyGetKey {
  return createRemoteJWKSet(new URL(jwksUrl));
}

/**
 * 既知の JWK 集合から鍵を引く(主に test 用、 鍵 rotation の無い専用 issuer でも有用)。
 */
export function createLocalJwks(jwks: Parameters<typeof createLocalJWKSet>[0]): JWTVerifyGetKey {
  return createLocalJWKSet(jwks);
}

function mapClaimsOrThrow(payload: JWTPayload): BffUserClaims {
  if (payload['token_use'] !== 'access') {
    throw new JwtVerificationError(
      `token_use が access ではない: ${String(payload['token_use'])}`,
    );
  }
  const tenantId = payload['tenant_id'];
  if (typeof tenantId !== 'string' || tenantId.length === 0) {
    throw new JwtVerificationError('tenant_id claim が無い');
  }
  const sub = payload.sub;
  if (typeof sub !== 'string' || sub.length === 0) {
    throw new JwtVerificationError('sub claim が無い');
  }
  const userId = Number(sub);
  if (!Number.isFinite(userId)) {
    throw new JwtVerificationError(`sub claim が数値変換できない: ${sub}`);
  }

  const roles = asStringArray(payload['roles'], 'roles');
  const rawScopes = payload['scopes'];
  const scopes =
    rawScopes && typeof rawScopes === 'object'
      ? {
          locations: asStringArray(
            (rawScopes as Record<string, unknown>)['locations'],
            'scopes.locations',
          ),
          partners: asStringArray(
            (rawScopes as Record<string, unknown>)['partners'],
            'scopes.partners',
          ),
        }
      : { locations: [], partners: [] };

  const mfaRaw = payload['mfa_strength'];
  const mfaStrength: BffUserClaims['mfaStrength'] =
    mfaRaw === 'medium' || mfaRaw === 'high' ? mfaRaw : 'low';

  const localeRaw = payload['locale'];
  const locale =
    typeof localeRaw === 'string' && localeRaw.length > 0 ? localeRaw : 'ja';

  return { userId, tenantId, roles, scopes, mfaStrength, locale };
}

function asStringArray(value: unknown, fieldLabel: string): string[] {
  if (value == null) return [];
  if (!Array.isArray(value)) {
    throw new JwtVerificationError(`${fieldLabel} claim が配列ではない`);
  }
  return value.map((v, i) => {
    if (typeof v !== 'string') {
      throw new JwtVerificationError(`${fieldLabel}[${i}] が string ではない`);
    }
    return v;
  });
}
