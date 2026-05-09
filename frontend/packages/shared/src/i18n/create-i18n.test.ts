import { describe, expect, it } from 'vitest';

import { createI18n } from './create-i18n.js';
import { defaultResources, mergeResources } from './resources.js';

describe('createI18n', () => {
  it('default ja で base catalog の文言を引ける', async () => {
    const i18n = await createI18n({ language: 'ja', resources: defaultResources });
    expect(i18n.t('auth.login')).toBe('ログイン');
    expect(i18n.t('ui.loading')).toBe('読み込み中...');
    expect(i18n.t('error.unknown')).toBe('予期しないエラーが発生しました');
  });

  it('en に切替えると英語の文言を引ける', async () => {
    const i18n = await createI18n({ language: 'en', resources: defaultResources });
    expect(i18n.t('auth.login')).toBe('Sign in');
    expect(i18n.t('ui.loading')).toBe('Loading...');
  });

  it('language 未指定時は fallback (default ja)', async () => {
    const i18n = await createI18n({ resources: defaultResources });
    expect(i18n.t('auth.logout')).toBe('ログアウト');
  });

  it('changeLanguage で実行時切替', async () => {
    const i18n = await createI18n({ language: 'ja', resources: defaultResources });
    expect(i18n.t('ui.cancel')).toBe('キャンセル');
    await i18n.changeLanguage('en');
    expect(i18n.t('ui.cancel')).toBe('Cancel');
  });

  it('未知 key は key 自体を返す(returnNull=false)', async () => {
    const i18n = await createI18n({ language: 'ja', resources: defaultResources });
    expect(i18n.t('not.exist.key')).toBe('not.exist.key');
  });
});

describe('mergeResources', () => {
  it('複数 source を浅く merge し、 同 namespace は後勝ち', () => {
    const a = { ja: { common: { x: '元' }, app: { y: 'a' } } };
    const b = { ja: { common: { x: '上書き', z: '新' } } };
    const merged = mergeResources(a, b);
    expect(merged['ja']).toEqual({
      common: { x: '上書き', z: '新' },
      app: { y: 'a' },
    });
  });

  it('言語 key 自体は union される', () => {
    const a = { ja: { common: { x: '日本語' } } };
    const b = { en: { common: { x: 'english' } } };
    const merged = mergeResources(a, b);
    expect(merged).toEqual({
      ja: { common: { x: '日本語' } },
      en: { common: { x: 'english' } },
    });
  });
});
