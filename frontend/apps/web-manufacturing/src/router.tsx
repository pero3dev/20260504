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
import { fetchWorkOrder } from './lib/graphql-client';

const rootRoute = createRootRoute({ component: RootLayout });

function RootLayout() {
  return (
    <AppShell brand="Manufacturing" nav={[{ to: '/', label: 'ダッシュボード' }]}>
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

function DashboardPage() {
  const { data, isLoading, error } = useQuery({
    queryKey: ['workOrder', '1'],
    queryFn: () => fetchWorkOrder('1'),
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
          <h2 className="text-lg font-semibold">
            WorkOrder {data.workOrder.code} (id: {data.workOrder.id})
          </h2>
          <dl className="grid grid-cols-2 gap-2 text-sm">
            <dt className="text-muted-foreground">完成品 SKU</dt>
            <dd>{data.workOrder.productSkuCode}</dd>
            <dt className="text-muted-foreground">拠点</dt>
            <dd>{data.workOrder.locationId}</dd>
            <dt className="text-muted-foreground">計画数量</dt>
            <dd>{data.workOrder.plannedQuantity.toLocaleString()}</dd>
            <dt className="text-muted-foreground">ステータス</dt>
            <dd>{data.workOrder.status}</dd>
            <dt className="text-muted-foreground">計画開始日</dt>
            <dd>{data.workOrder.plannedStartDate ?? '-'}</dd>
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
