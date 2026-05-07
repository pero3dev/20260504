import { AppShell, DataTable } from '@inventory/ui';
import { useQuery } from '@tanstack/react-query';
import {
  createRootRoute,
  createRoute,
  createRouter,
  Outlet,
} from '@tanstack/react-router';

import { fetchSku } from './lib/graphql-client';

const rootRoute = createRootRoute({
  component: RootLayout,
});

function RootLayout() {
  return (
    <AppShell brand="Retail/EC" nav={[{ to: '/', label: 'ダッシュボード' }]}>
      <Outlet />
    </AppShell>
  );
}

const indexRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/',
  component: DashboardPage,
});

function DashboardPage() {
  const { data, isLoading, error } = useQuery({
    queryKey: ['sku', 'SKU-1'],
    queryFn: () => fetchSku('SKU-1'),
  });

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">在庫ダッシュボード</h1>
        <p className="text-sm text-muted-foreground">
          BFF(/graphql)経由で SKU 在庫を取得しています。
        </p>
      </div>

      {isLoading && <p className="text-muted-foreground">読み込み中...</p>}
      {error && (
        <p className="rounded-lg border border-border bg-muted p-4 text-sm text-muted-foreground">
          BFF 取得失敗: {error instanceof Error ? error.message : String(error)}
        </p>
      )}

      {data?.sku && (
        <section className="space-y-3">
          <h2 className="text-lg font-semibold">{data.sku.displayName}</h2>
          <DataTable
            rows={data.sku.inventories}
            rowKey={(inv) => inv.locationId}
            columns={[
              { header: 'Location', render: (inv) => inv.locationId },
              { header: 'Available', render: (inv) => inv.available },
              { header: 'Reserved', render: (inv) => inv.reserved },
              {
                header: 'Updated',
                render: (inv) => (
                  <span className="text-muted-foreground">
                    {new Date(inv.updatedAt).toLocaleString('ja-JP')}
                  </span>
                ),
              },
            ]}
          />
        </section>
      )}
    </div>
  );
}

const routeTree = rootRoute.addChildren([indexRoute]);

export const router = createRouter({ routeTree, defaultPreload: 'intent' });

declare module '@tanstack/react-router' {
  interface Register {
    router: typeof router;
  }
}
