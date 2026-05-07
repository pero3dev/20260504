import { afterEach, describe, expect, it } from 'vitest';

import { clearAuthToken, getAuthToken, setAuthToken } from './auth';

describe('auth helpers', () => {
  afterEach(() => {
    window.localStorage.clear();
  });

  it('round-trip', () => {
    setAuthToken('mfg-jwt-1');
    expect(getAuthToken()).toBe('mfg-jwt-1');
  });

  it('clear', () => {
    setAuthToken('mfg-jwt-2');
    clearAuthToken();
    expect(getAuthToken()).toBeNull();
  });
});
