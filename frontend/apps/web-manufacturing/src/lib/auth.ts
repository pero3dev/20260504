import {
  createAuthManager,
  readOidcConfigFromEnv,
  type AuthManager,
} from '@inventory/shared/web-auth';

export const authManager: AuthManager = createAuthManager(
  readOidcConfigFromEnv(import.meta.env as unknown as Record<string, unknown>),
  'web-manufacturing-auth',
);

export function getAuthToken(): string | null {
  return authManager.getAccessToken();
}
