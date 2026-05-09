import type { Resource } from 'i18next';

import en from './locales/en/common.json' with { type: 'json' };
import ja from './locales/ja/common.json' with { type: 'json' };

/**
 * 4 web app で共通の base resources。 業態 namespace は各 web app が個別に
 * 追加して `createI18n({ resources: mergeResources(defaultResources, ...) })` で渡す。
 */
export const defaultResources: Resource = {
  ja: { common: ja },
  en: { common: en },
};

/**
 * 言語ごとに浅く merge する helper。 同 namespace 内の key 衝突は **後勝ち**。
 *
 * @example
 *   const myAppResources = { ja: { 'retail-ec': retailJa }, en: { 'retail-ec': retailEn } };
 *   const merged = mergeResources(defaultResources, myAppResources);
 */
export function mergeResources(...sources: Resource[]): Resource {
  const out: Resource = {};
  for (const src of sources) {
    for (const [lang, namespaces] of Object.entries(src)) {
      out[lang] = { ...(out[lang] ?? {}), ...namespaces };
    }
  }
  return out;
}
