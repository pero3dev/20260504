import i18next, { type i18n, type Resource } from 'i18next';
import { initReactI18next } from 'react-i18next';

/**
 * Vite ビルド時に web app 単位で 1 度だけ呼ぶ factory(ADR-0022 phase 1)。
 *
 * <p>言語は **テナント単位固定** が原則。 Identity Broker の `tenant.locale` claim を
 * 起動時に取り出して `language` に渡す(MVP では `ja` 固定で OK)。 user 単位切替は後フェーズ。
 *
 * <p>browser language detector は採用しない:
 *
 * <ul>
 *   <li>テナント運用言語が組織で固定される B2B SaaS では browser hint が誤誘導になる
 *   <li>SSR 不在で `<html lang>` を mount 後に変えても OK なので detector の cookie 永続化が不要
 * </ul>
 */
export interface CreateI18nOptions {
  /** 表示言語(`ja` / `en` 等)。 省略時は fallback */
  language?: string;
  /** fallback 言語(default `ja`)。 翻訳キー欠落時にこの言語で穴埋め */
  fallbackLanguage?: string;
  /** Resource(language → namespace → key map)。 default は `defaultResources`(ja/en × common 1 NS) */
  resources?: Resource;
}

export async function createI18n(options: CreateI18nOptions = {}): Promise<i18n> {
  const instance = i18next.createInstance();
  await instance.use(initReactI18next).init({
    fallbackLng: options.fallbackLanguage ?? 'ja',
    defaultNS: 'common',
    ns: ['common'],
    interpolation: {
      escapeValue: false, // React は JSX 経由で XSS 防御済
    },
    returnNull: false,
    ...(options.resources !== undefined ? { resources: options.resources } : {}),
    ...(options.language !== undefined ? { lng: options.language } : {}),
  });
  return instance;
}
