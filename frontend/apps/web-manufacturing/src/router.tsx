import { AppShell } from '@inventory/ui';
import { useQuery } from '@tanstack/react-query';
import {
  createRootRoute,
  createRoute,
  createRouter,
  Outlet,
} from '@tanstack/react-router';

import { fetchWorkOrder } from './lib/graphql-client';

const rootRoute = createRootRoute({ component: RootLayout });

function RootLayout() {
  return (
    <AppShell brand="Manufacturing" nav={[{ to: '/', label: 'ダッシュボード' }]}>
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
    queryKey: ['workOrder', 'WO-1'],
    queryFn: () => fetchWorkOrder('WO-1'),
  });

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">WorkOrder ダッシュボード</h1>
        <p className="text-sm text-muted-foreground">
          bff-manufacturing 経由で WorkOrder を取得しています。
        </p>
      </div>

      {isLoading && <p className="text-muted-foreground">読み込み中...</p>}
      {error && (
        <p className="rounded-lg border border-border bg-muted p-4 text-sm text-muted-foreground">
          BFF 取得失敗: {error instanceof Error ? error.message : String(error)}
        </p>
      )}

      {data?.workOrder && (
        <section className="space-y-3 rounded-lg border border-border p-4">
          <h2 className="text-lg font-semibold">WorkOrder {data.workOrder.workOrderId}</h2>
          <dl className="grid grid-cols-2 gap-2 text-sm">
            <dt className="text-muted-foreground">完成品 SKU</dt>
            <dd>{data.workOrder.productSkuId}</dd>
            <dt className="text-muted-foreground">ステータス</dt>
            <dd>{data.workOrder.status}</dd>
            <dt className="text-muted-foreground">開始時刻</dt>
            <dd>{new Date(data.workOrder.startedAt).toLocaleString('ja-JP')}</dd>
            <dt className="text-muted-foreground">完了時刻</dt>
            <dd>{data.workOrder.completedAt ?? '-'}</dd>
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
