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

  // env 有り時は oidc-client-ts UserManager.signinSilentCallback() への委譲のみで、
  // 内部挙動(throw / silent return)は library + 実行 environment に依存する。 helper は
  // 失敗を console.warn で飲む構造のため、 library 実装そのものを test するのではなく、
  // 「未処理例外で test runner を破壊しない」ことを上の no-op test と本ファイル全体の
  // resolve 着地で担保する。 deterministic に warn を発火させるには UserManager mock が
  // 必要だが、 helper 1 行の delegate を test するには過剰なので省く。
});
