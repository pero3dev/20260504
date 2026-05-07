import { exportJWK, generateKeyPair, SignJWT, type KeyLike } from 'jose';
import { afterEach, beforeAll, describe, expect, it, vi } from 'vitest';

import { createJwtVerifier, JwtVerificationError } from './verify-jwt.js';

interface SignOpts {
  iss?: string;
  aud?: string;
  sub?: string;
  tokenUse?: string;
  tenantId?: string | undefined;
  roles?: unknown;
  scopes?: unknown;
  mfaStrength?: unknown;
  expSecondsFromNow?: number;
  kid?: string;
}

const ISSUER = 'https://idp.example.test/';
const AUDIENCE = 'tenant-acme';
const JWKS_URL = 'https://idp.example.test/.well-known/jwks.json';

let privateKey: KeyLike;
let publicJwk: Record<string, unknown>;

beforeAll(async () => {
  const pair = await generateKeyPair('RS256');
  privateKey = pair.privateKey;
  const exported = await exportJWK(pair.publicKey);
  publicJwk = { ...exported, kid: 'test-key-1', alg: 'RS256', use: 'sig' };
});

async function sign(opts: SignOpts = {}): Promise<string> {
  const payload: Record<string, unknown> = {
    token_use: opts.tokenUse ?? 'access',
    roles: opts.roles ?? ['ROLE_USER'],
    scopes: opts.scopes ?? { locations: ['tokyo'], partners: [] },
    mfa_strength: opts.mfaStrength ?? 'low',
  };
  if (opts.tenantId !== undefined) payload['tenant_id'] = opts.tenantId;
  else payload['tenant_id'] = AUDIENCE;

  const builder = new SignJWT(payload)
    .setProtectedHeader({ alg: 'RS256', kid: opts.kid ?? 'test-key-1' })
    .setIssuer(opts.iss ?? ISSUER)
    .setSubject(opts.sub ?? '42')
    .setIssuedAt()
    .setExpirationTime(`${opts.expSecondsFromNow ?? 60}s`);
  if (opts.aud !== undefined) builder.setAudience(opts.aud);
  return builder.sign(privateKey);
}

function stubJwksFetch() {
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string | URL) => {
      if (String(url) !== JWKS_URL) {
        return new Response('not found', { status: 404 });
      }
      return new Response(JSON.stringify({ keys: [publicJwk] }), {
        status: 200,
        headers: { 'content-type': 'application/json' },
      });
    }),
  );
}

describe('createJwtVerifier', () => {
  afterEach(() => vi.restoreAllMocks());

  it('正常系: token_use=access + tenant_id + roles + scopes を mapping する', async () => {
    stubJwksFetch();
    const verify = createJwtVerifier({ jwksUrl: JWKS_URL, issuer: ISSUER });
    const token = await sign({
      sub: '42',
      tenantId: 'tenant-acme',
      roles: ['ROLE_USER', 'ROLE_OPS'],
      scopes: { locations: ['tokyo', 'osaka'], partners: ['p-1'] },
      mfaStrength: 'medium',
    });

    const claims = await verify(token);

    expect(claims.userId).toBe(42);
    expect(claims.tenantId).toBe('tenant-acme');
    expect(claims.roles).toEqual(['ROLE_USER', 'ROLE_OPS']);
    expect(claims.scopes).toEqual({ locations: ['tokyo', 'osaka'], partners: ['p-1'] });
    expect(claims.mfaStrength).toBe('medium');
  });

  it('audience option を渡すと一致しないトークンは reject', async () => {
    stubJwksFetch();
    const verify = createJwtVerifier({
      jwksUrl: JWKS_URL,
      issuer: ISSUER,
      audience: AUDIENCE,
    });
    const wrong = await sign({ aud: 'other-tenant' });
    await expect(verify(wrong)).rejects.toBeInstanceOf(JwtVerificationError);
  });

  it('期限切れは reject', async () => {
    stubJwksFetch();
    const verify = createJwtVerifier({ jwksUrl: JWKS_URL, issuer: ISSUER });
    const expired = await sign({ expSecondsFromNow: -120 });
    await expect(verify(expired)).rejects.toBeInstanceOf(JwtVerificationError);
  });

  it('iss が違うと reject', async () => {
    stubJwksFetch();
    const verify = createJwtVerifier({ jwksUrl: JWKS_URL, issuer: ISSUER });
    const wrongIss = await sign({ iss: 'https://other-idp.example/' });
    await expect(verify(wrongIss)).rejects.toBeInstanceOf(JwtVerificationError);
  });

  it('token_use=session(セッショントークン)は reject', async () => {
    stubJwksFetch();
    const verify = createJwtVerifier({ jwksUrl: JWKS_URL, issuer: ISSUER });
    const session = await sign({ tokenUse: 'session' });
    await expect(verify(session)).rejects.toThrow(/token_use/);
  });

  it('tenant_id 欠落は reject', async () => {
    stubJwksFetch();
    const verify = createJwtVerifier({ jwksUrl: JWKS_URL, issuer: ISSUER });
    // tenant_id をわざと undefined のまま payload を組む
    const token = await new SignJWT({
      token_use: 'access',
      roles: ['ROLE_USER'],
      scopes: { locations: [], partners: [] },
    })
      .setProtectedHeader({ alg: 'RS256', kid: 'test-key-1' })
      .setIssuer(ISSUER)
      .setSubject('42')
      .setIssuedAt()
      .setExpirationTime('60s')
      .sign(privateKey);

    await expect(verify(token)).rejects.toThrow(/tenant_id/);
  });
});
