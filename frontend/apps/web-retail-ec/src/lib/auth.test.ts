import { afterEach, describe, expect, it } from 'vitest';

import { clearAuthToken, getAuthToken, setAuthToken } from './auth';

describe('auth helpers', () => {
  afterEach(() => {
    window.localStorage.clear();
  });

  it('setAuthToken → getAuthToken で round-trip する', () => {
    setAuthToken('dev-jwt-1');
    expect(getAuthToken()).toBe('dev-jwt-1');
  });

  it('clearAuthToken で localStorage が空になる', () => {
    setAuthToken('dev-jwt-2');
    clearAuthToken();
    expect(getAuthToken()).toBeNull();
  });
});
