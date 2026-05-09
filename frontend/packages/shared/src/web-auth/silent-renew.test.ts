// @vitest-environment jsdom
import { describe, expect, it, vi } from 'vitest';

import { runSilentRenewCallback } from './silent-renew.js';

describe('runSilentRenewCallback', () => {
  it('OIDC env が無いと no-op で resolve(dev fallback 想定)', async () => {
    const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {});
    await expect(runSilentRenewCallback({})).resolves.toBeUndefined();
    expect(warnSpy).not.toHaveBeenCalled();
    warnSpy.mockRestore();
  });

  it('OIDC env が揃っていても callback URL に code が無ければ警告を出して飲む', async () => {
    const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {});
    // jsdom の window.location には search/hash が無いので signinSilentCallback は失敗する想定。
    // throw を helper が呑むことを検証(parent 側 silentRenewError にも別経路で伝わる)。
    await runSilentRenewCallback({
      VITE_OIDC_AUTHORITY: 'https://idp.example/',
      VITE_OIDC_CLIENT_ID: 'web-retail-ec',
      VITE_OIDC_REDIRECT_URI: 'https://app.example/callback',
    });
    expect(warnSpy).toHaveBeenCalled();
    warnSpy.mockRestore();
  });
});
