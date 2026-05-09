import type { Resource } from 'i18next';

import enManufacturing from './locales/en/manufacturing.json' with { type: 'json' };
import enRetailEc from './locales/en/retail-ec.json' with { type: 'json' };
import enTpl from './locales/en/tpl.json' with { type: 'json' };
import enWholesale from './locales/en/wholesale.json' with { type: 'json' };
import jaManufacturing from './locales/ja/manufacturing.json' with { type: 'json' };
import jaRetailEc from './locales/ja/retail-ec.json' with { type: 'json' };
import jaTpl from './locales/ja/tpl.json' with { type: 'json' };
import jaWholesale from './locales/ja/wholesale.json' with { type: 'json' };

/**
 * 4 業態 web app それぞれが追加で読み込む resources。 各 web app は
 * `mergeResources(defaultResources, retailEcResources)` 等で共通 + 業態を 1 instance に統合。
 */
export const retailEcResources: Resource = {
  ja: { 'retail-ec': jaRetailEc },
  en: { 'retail-ec': enRetailEc },
};

export const manufacturingResources: Resource = {
  ja: { manufacturing: jaManufacturing },
  en: { manufacturing: enManufacturing },
};

export const tplResources: Resource = {
  ja: { tpl: jaTpl },
  en: { tpl: enTpl },
};

export const wholesaleResources: Resource = {
  ja: { wholesale: jaWholesale },
  en: { wholesale: enWholesale },
};
