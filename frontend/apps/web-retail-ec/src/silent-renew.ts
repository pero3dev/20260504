import { runSilentRenewCallback } from '@inventory/shared/web-auth';

// hidden iframe で実行される。 React app は mount しない。
void runSilentRenewCallback(import.meta.env as unknown as Record<string, unknown>);
