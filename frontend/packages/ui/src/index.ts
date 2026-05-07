// Public re-export hub for `@inventory/ui`. 各 web app は
//   import { AppShell, Button, DataTable, cn } from '@inventory/ui';
// で使用する。
export { cn } from './lib/cn.js';
export { AppShell, type AppShellNavItem } from './components/app-shell.js';
export { Button, type ButtonVariant } from './components/button.js';
export { DataTable, type DataTableColumn } from './components/data-table.js';
export {
  AuthButtons,
  type AuthButtonsManager,
  type AuthButtonsProps,
} from './components/auth-buttons.js';
export {
  OidcCallbackPage,
  type OidcCallbackManager,
  type OidcCallbackPageProps,
} from './components/oidc-callback-page.js';
