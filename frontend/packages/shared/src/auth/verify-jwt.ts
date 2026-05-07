import { createRemoteJWKSet, jwtVerify, type JWTPayload, type JWTVerifyResult } from 'jose';

/**
 * BFF が verify 後に context として持つ user 情報。 Identity Broker (`NimbusJwtTokenIssuer`)
 * の access token claim と一致(`token_use=access`、 `tenant_id`、 `roles[]`、
 * `scopes.{locations,partners}`、 `mfa_strength`)。
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
}

export interface JwtVerifierOptions {
  /** Identity Broker の JWKS URL(例: `http://identity-broker:8081/.well-known/jwks.json`) */
  jwksUrl: string;
  /** 期待する `iss`(`platform.identity.issuer` と一致させる) */
  issuer: string;
  /**
   * 期待する `aud`(BFF は通常テナント単位で受信するわけではないため未指定可)。
   * 指定するとテナント横断トークン(token_use=session)の混入を防げる。
   */
  audience?: string;
  /** 許容する clock skew(秒、 default 30) */
  clockToleranceSeconds?: number;
}

export type JwtVerifier = (token: string) => Promise<BffUserClaims>;

export class JwtVerificationError extends Error {
  constructor(
    message: string,
    public readonly cause?: unknown,
  ) {
    super(message);
    this.name = 'JwtVerificationError';
  }
}

/**
 * Identity Broker の RS256 アクセストークンを verify する verifier を返す。
 *
 * <p>JWKS は `jose.createRemoteJWKSet` がメモリにキャッシュし、 鍵 rotation 時のみ再取得する
 * (default cooldown 30s)。 BFF が cold start 時に identity-broker へ 1 回だけ HTTP し、
 * 以降は in-process 検証で完結する。
 */
export function createJwtVerifier(options: JwtVerifierOptions): JwtVerifier {
  const { jwksUrl, issuer, audience, clockToleranceSeconds = 30 } = options;
  const jwks = createRemoteJWKSet(new URL(jwksUrl));

  return async (token: string): Promise<BffUserClaims> => {
    let result: JWTVerifyResult;
    try {
      result = await jwtVerify(token, jwks, {
        issuer,
        audience,
        algorithms: ['RS256'],
        clockTolerance: clockToleranceSeconds,
      });
    } catch (err) {
      throw new JwtVerificationError(
        `JWT verify に失敗: ${err instanceof Error ? err.message : String(err)}`,
        err,
      );
    }
    return mapClaimsOrThrow(result.payload);
  };
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

  return { userId, tenantId, roles, scopes, mfaStrength };
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
