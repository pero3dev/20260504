// `@inventory/shared/i18n` subpath エントリ。 web app 専用(BFF からは import しない)。
//
// 標準 import:
//   import { createI18n, defaultResources, mergeResources, retailEcResources } from '@inventory/shared/i18n';
//
// 業態特有 namespace を追加する場合は mergeResources(defaultResources, ...) で resources を組み立てて createI18n に渡す。
export * from './create-i18n.js';
export * from './resources.js';
export * from './app-resources.js';
