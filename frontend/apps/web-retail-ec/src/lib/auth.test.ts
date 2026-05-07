import { afterEach, describe, expect, it } from 'vitest';

import { authManager, getAuthToken } from './auth';

// Vite test 環境では VITE_OIDC_* env 未設定のため dev fallback が選ばれる。
describe('web-retail-ec auth (dev fallback)', () => {
  afterEach(() => window.sessionStorage.clear());

  it('initial state は未認証', () => {
    expect(getAuthToken()).toBeNull();
    expect(authManager.isAuthenticated()).toBe(false);
  });

  it('signIn で dev token が発行される', async () => {
    await authManager.signIn();
    expect(getAuthToken()).toMatch(/^dev-token-/);
  });

  it('signOut で token が消える', async () => {
    await authManager.signIn();
    await authManager.signOut();
    expect(getAuthToken()).toBeNull();
  });
});
