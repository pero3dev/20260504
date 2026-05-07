// @vitest-environment jsdom
import { afterEach, describe, expect, it } from 'vitest';

import { createAuthManager, readOidcConfigFromEnv } from './auth-manager.js';

describe('readOidcConfigFromEnv', () => {
  it('全 3 必須項目が揃っていると OidcConfig を返す', () => {
    const cfg = readOidcConfigFromEnv({
      VITE_OIDC_AUTHORITY: 'https://idp.example/',
      VITE_OIDC_CLIENT_ID: 'web-retail-ec',
      VITE_OIDC_REDIRECT_URI: 'https://app.example/callback',
    });
    expect(cfg).toEqual({
      authority: 'https://idp.example/',
      clientId: 'web-retail-ec',
      redirectUri: 'https://app.example/callback',
    });
  });

  it('1 項目でも欠けると null', () => {
    expect(
      readOidcConfigFromEnv({
        VITE_OIDC_AUTHORITY: 'https://idp.example/',
        VITE_OIDC_CLIENT_ID: 'web-retail-ec',
      }),
    ).toBeNull();
  });

  it('postLogoutRedirectUri / scope は任意で含める', () => {
    const cfg = readOidcConfigFromEnv({
      VITE_OIDC_AUTHORITY: 'https://idp.example/',
      VITE_OIDC_CLIENT_ID: 'web-retail-ec',
      VITE_OIDC_REDIRECT_URI: 'https://app.example/callback',
      VITE_OIDC_POST_LOGOUT_REDIRECT_URI: 'https://app.example/',
      VITE_OIDC_SCOPE: 'openid profile email',
    });
    expect(cfg?.postLogoutRedirectUri).toBe('https://app.example/');
    expect(cfg?.scope).toBe('openid profile email');
  });

  it('空文字は無視', () => {
    expect(
      readOidcConfigFromEnv({
        VITE_OIDC_AUTHORITY: '',
        VITE_OIDC_CLIENT_ID: 'web-retail-ec',
        VITE_OIDC_REDIRECT_URI: 'https://app.example/callback',
      }),
    ).toBeNull();
  });
});

describe('createAuthManager (dev fallback)', () => {
  afterEach(() => window.sessionStorage.clear());

  it('config null だと dev fallback を返し signIn → token 発行', async () => {
    const m = createAuthManager(null, 'test-app-token');
    expect(m.isAuthenticated()).toBe(false);
    expect(m.getAccessToken()).toBeNull();

    await m.signIn();

    expect(m.isAuthenticated()).toBe(true);
    expect(m.getAccessToken()).toMatch(/^dev-token-/);
  });

  it('signOut で token が消える', async () => {
    const m = createAuthManager(null, 'test-app-token');
    await m.signIn();
    expect(m.getAccessToken()).not.toBeNull();
    await m.signOut();
    expect(m.getAccessToken()).toBeNull();
  });

  it('dev mode の handleCallback は no-op で throw しない', async () => {
    const m = createAuthManager(null, 'test-app-token');
    await expect(m.handleCallback()).resolves.toBeUndefined();
  });

  it('dev fallback は app ごとに異なる sessionStorage key で分離する', async () => {
    const a = createAuthManager(null, 'app-a-token');
    const b = createAuthManager(null, 'app-b-token');
    await a.signIn();
    expect(a.isAuthenticated()).toBe(true);
    expect(b.isAuthenticated()).toBe(false);
  });
});
