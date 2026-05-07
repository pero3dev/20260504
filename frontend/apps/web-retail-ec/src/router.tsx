import { AppShell, AuthButtons, OidcCallbackPage } from '@inventory/ui';
import { useQuery } from '@tanstack/react-query';
import {
  createRootRoute,
  createRoute,
  createRouter,
  Outlet,
  useNavigate,
} from '@tanstack/react-router';

import { authManager } from './lib/auth';
import { fetchInventory } from './lib/graphql-client';

const rootRoute = createRootRoute({ component: RootLayout });

function RootLayout() {
  return (
    <AppShell brand="Retail/EC" nav={[{ to: '/', label: 'ダッシュボード' }]}>
      <div className="mb-4 flex justify-end">
        <AuthButtons authManager={authManager} />
      </div>
      <Outlet />
    </AppShell>
  );
}

const indexRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/',
  component: DashboardPage,
});

const callbackRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/callback',
  component: CallbackPage,
});

function CallbackPage() {
  const navigate = useNavigate();
  return (
    <OidcCallbackPage
      authManager={authManager}
      onSuccess={() => void navigate({ to: '/', replace: true })}
    />
  );
}

const DEFAULT_INVENTORY_ID = '1';

function DashboardPage() {
  const { data, isLoading, error } = useQuery({
    queryKey: ['inventory', DEFAULT_INVENTORY_ID],
    queryFn: () => fetchInventory(DEFAULT_INVENTORY_ID),
  });

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">在庫ダッシュボード</h1>
        <p className="text-sm text-muted-foreground">
          BFF(/graphql)経由で inventory-read-model から実在庫を取得しています。
        </p>
      </div>

      {isLoading && <p className="text-muted-foreground">読み込み中...</p>}
      {error && (
        <p className="rounded-lg border border-border bg-muted p-4 text-sm text-muted-foreground">
          BFF 取得失敗: {error instanceof Error ? error.message : String(error)}
        </p>
      )}

      {data?.inventory === null && !isLoading && (
        <p className="rounded-lg border border-border bg-muted p-4 text-sm text-muted-foreground">
          inventoryId={DEFAULT_INVENTORY_ID} に該当する在庫はありません。
        </p>
      )}

      {data?.inventory && (
        <section className="space-y-3 rounded-lg border border-border p-4">
          <h2 className="text-lg font-semibold">Inventory {data.inventory.id}</h2>
          <dl className="grid grid-cols-2 gap-2 text-sm">
            <dt className="text-muted-foreground">SKU</dt>
            <dd>{data.inventory.skuId}</dd>
            <dt className="text-muted-foreground">Location</dt>
            <dd>{data.inventory.locationId}</dd>
            <dt className="text-muted-foreground">Available</dt>
            <dd>{data.inventory.available}</dd>
            <dt className="text-muted-foreground">Reserved</dt>
            <dd>{data.inventory.reserved}</dd>
            <dt className="text-muted-foreground">Version</dt>
            <dd>{data.inventory.version}</dd>
          </dl>
        </section>
      )}
    </div>
  );
}

const routeTree = rootRoute.addChildren([indexRoute, callbackRoute]);
export const router = createRouter({ routeTree, defaultPreload: 'intent' });

declare module '@tanstack/react-router' {
  interface Register {
    router: typeof router;
  }
}
