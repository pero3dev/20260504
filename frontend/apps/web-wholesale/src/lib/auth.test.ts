import { afterEach, describe, expect, it } from 'vitest';

import { clearAuthToken, getAuthToken, setAuthToken } from './auth';

describe('auth helpers', () => {
  afterEach(() => {
    window.localStorage.clear();
  });

  it('round-trip', () => {
    setAuthToken('ws-jwt-1');
    expect(getAuthToken()).toBe('ws-jwt-1');
  });

  it('clear', () => {
    setAuthToken('ws-jwt-2');
    clearAuthToken();
    expect(getAuthToken()).toBeNull();
  });
});
