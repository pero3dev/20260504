import { AppShell } from '@inventory/ui';
import { useQuery } from '@tanstack/react-query';
import {
  createRootRoute,
  createRoute,
  createRouter,
  Outlet,
} from '@tanstack/react-router';

import { fetchSalesOrder } from './lib/graphql-client';

const rootRoute = createRootRoute({ component: RootLayout });

function RootLayout() {
  return (
    <AppShell brand="Wholesale" nav={[{ to: '/', label: 'ダッシュボード' }]}>
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
    queryKey: ['salesOrder', 'SO-1'],
    queryFn: () => fetchSalesOrder('SO-1'),
  });

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">受注ダッシュボード</h1>
        <p className="text-sm text-muted-foreground">
          bff-wholesale 経由で SalesOrder を取得しています。
        </p>
      </div>

      {isLoading && <p className="text-muted-foreground">読み込み中...</p>}
      {error && (
        <p className="rounded-lg border border-border bg-muted p-4 text-sm text-muted-foreground">
          BFF 取得失敗: {error instanceof Error ? error.message : String(error)}
        </p>
      )}

      {data?.salesOrder && (
        <section className="space-y-3 rounded-lg border border-border p-4">
          <h2 className="text-lg font-semibold">SalesOrder {data.salesOrder.salesOrderId}</h2>
          <dl className="grid grid-cols-2 gap-2 text-sm">
            <dt className="text-muted-foreground">取引先</dt>
            <dd>{data.salesOrder.partnerId}</dd>
            <dt className="text-muted-foreground">ステータス</dt>
            <dd>{data.salesOrder.status}</dd>
            <dt className="text-muted-foreground">金額(JPY)</dt>
            <dd>{data.salesOrder.totalAmountJpy.toLocaleString('ja-JP')} 円</dd>
            <dt className="text-muted-foreground">受注時刻</dt>
            <dd>{new Date(data.salesOrder.placedAt).toLocaleString('ja-JP')}</dd>
            <dt className="text-muted-foreground">出荷時刻</dt>
            <dd>{data.salesOrder.shippedAt ?? '-'}</dd>
          </dl>
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
