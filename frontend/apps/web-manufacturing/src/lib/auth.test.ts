import { afterEach, describe, expect, it } from 'vitest';

import { authManager, getAuthToken } from './auth';

describe('web-manufacturing auth (dev fallback)', () => {
  afterEach(() => window.sessionStorage.clear());

  it('initial state は未認証', () => {
    expect(getAuthToken()).toBeNull();
    expect(authManager.isAuthenticated()).toBe(false);
  });

  it('signIn → dev token 発行 → signOut でクリア', async () => {
    await authManager.signIn();
    expect(getAuthToken()).toMatch(/^dev-token-/);
    await authManager.signOut();
    expect(getAuthToken()).toBeNull();
  });
});
