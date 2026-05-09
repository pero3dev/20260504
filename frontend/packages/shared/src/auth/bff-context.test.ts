import { describe, expect, it, vi } from 'vitest';

import { buildBffAuth } from './bff-context.js';
import type { BffUserClaims, JwtVerifier } from './verify-jwt.js';

const SAMPLE_CLAIMS: BffUserClaims = {
  userId: 42,
  tenantId: 'tenant-acme',
  roles: ['ROLE_USER'],
  scopes: { locations: ['tokyo'], partners: [] },
  mfaStrength: 'low',
  locale: 'ja',
};

describe('buildBffAuth', () => {
  it('Authorization 無し + verifier 無し → token=null / user=null', async () => {
    const result = await buildBffAuth({ authorizationHeader: undefined, verifier: null });
    expect(result).toEqual({ authToken: null, user: null });
  });

  it('Bearer 有り + verifier 無し → token は抽出 / user=null', async () => {
    const result = await buildBffAuth({
      authorizationHeader: 'Bearer abc.def.ghi',
      verifier: null,
    });
    expect(result.authToken).toBe('abc.def.ghi');
    expect(result.user).toBeNull();
  });

  it('Bearer 有り + verifier 有り → user 取出 + token も保持', async () => {
    const verifier: JwtVerifier = vi.fn(async () => SAMPLE_CLAIMS);
    const result = await buildBffAuth({
      authorizationHeader: 'Bearer abc.def.ghi',
      verifier,
    });
    expect(verifier).toHaveBeenCalledWith('abc.def.ghi');
    expect(result.authToken).toBe('abc.def.ghi');
    expect(result.user).toEqual(SAMPLE_CLAIMS);
  });

  it('Authorization 無し + verifier 有り → token=null / user=null(verify は走らない)', async () => {
    const verifier: JwtVerifier = vi.fn(async () => SAMPLE_CLAIMS);
    const result = await buildBffAuth({ authorizationHeader: undefined, verifier });
    expect(verifier).not.toHaveBeenCalled();
    expect(result).toEqual({ authToken: null, user: null });
  });

  it('verifier が throw すると propagate する(呼出側で 401 マッピング)', async () => {
    const verifier: JwtVerifier = vi.fn(async () => {
      throw new Error('boom');
    });
    await expect(
      buildBffAuth({ authorizationHeader: 'Bearer x', verifier }),
    ).rejects.toThrow(/boom/);
  });
});
