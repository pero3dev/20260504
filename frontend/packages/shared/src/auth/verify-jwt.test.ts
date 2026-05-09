import { exportJWK, generateKeyPair, SignJWT, type JWK, type KeyLike } from 'jose';
import { beforeAll, describe, expect, it } from 'vitest';

import {
  createJwtVerifier,
  createLocalJwks,
  JwtVerificationError,
} from './verify-jwt.js';

interface SignOpts {
  iss?: string;
  aud?: string;
  sub?: string;
  tokenUse?: string;
  tenantId?: string | undefined;
  roles?: unknown;
  scopes?: unknown;
  mfaStrength?: unknown;
  locale?: string;
  expSecondsFromNow?: number;
  kid?: string;
}

const ISSUER = 'https://idp.example.test/';
const AUDIENCE = 'tenant-acme';

let privateKey: KeyLike;
let publicJwk: JWK;

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
  if (opts.locale !== undefined) payload['locale'] = opts.locale;

  const builder = new SignJWT(payload)
    .setProtectedHeader({ alg: 'RS256', kid: opts.kid ?? 'test-key-1' })
    .setIssuer(opts.iss ?? ISSUER)
    .setSubject(opts.sub ?? '42')
    .setIssuedAt()
    .setExpirationTime(`${opts.expSecondsFromNow ?? 60}s`);
  if (opts.aud !== undefined) builder.setAudience(opts.aud);
  return builder.sign(privateKey);
}

function buildVerifier(audience?: string) {
  // 引数有無で audience option を切り替え(exactOptionalPropertyTypes 適合)。
  const jwks = createLocalJwks({ keys: [publicJwk] });
  return audience === undefined
    ? createJwtVerifier({ jwks, issuer: ISSUER })
    : createJwtVerifier({ jwks, issuer: ISSUER, audience });
}

describe('createJwtVerifier', () => {
  it('正常系: token_use=access + tenant_id + roles + scopes + locale を mapping する', async () => {
    const verify = buildVerifier();
    const token = await sign({
      sub: '42',
      tenantId: 'tenant-acme',
      roles: ['ROLE_USER', 'ROLE_OPS'],
      scopes: { locations: ['tokyo', 'osaka'], partners: ['p-1'] },
      mfaStrength: 'medium',
      locale: 'en',
    });

    const claims = await verify(token);

    expect(claims.userId).toBe(42);
    expect(claims.tenantId).toBe('tenant-acme');
    expect(claims.roles).toEqual(['ROLE_USER', 'ROLE_OPS']);
    expect(claims.scopes).toEqual({ locations: ['tokyo', 'osaka'], partners: ['p-1'] });
    expect(claims.mfaStrength).toBe('medium');
    expect(claims.locale).toBe('en');
  });

  it('locale claim 欠落時は ja fallback', async () => {
    const verify = buildVerifier();
    // claim を立てずに sign(SignOpts.locale 未指定 = payload に含まれない)
    const token = await sign();
    const claims = await verify(token);
    expect(claims.locale).toBe('ja');
  });

  it('audience option を渡すと一致しないトークンは reject', async () => {
    const verify = buildVerifier(AUDIENCE);
    const wrong = await sign({ aud: 'other-tenant' });
    await expect(verify(wrong)).rejects.toBeInstanceOf(JwtVerificationError);
  });

  it('期限切れは reject', async () => {
    const verify = buildVerifier();
    const expired = await sign({ expSecondsFromNow: -120 });
    await expect(verify(expired)).rejects.toBeInstanceOf(JwtVerificationError);
  });

  it('iss が違うと reject', async () => {
    const verify = buildVerifier();
    const wrongIss = await sign({ iss: 'https://other-idp.example/' });
    await expect(verify(wrongIss)).rejects.toBeInstanceOf(JwtVerificationError);
  });

  it('token_use=session(セッショントークン)は reject', async () => {
    const verify = buildVerifier();
    const session = await sign({ tokenUse: 'session' });
    await expect(verify(session)).rejects.toThrow(/token_use/);
  });

  it('tenant_id 欠落は reject', async () => {
    const verify = buildVerifier();
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
