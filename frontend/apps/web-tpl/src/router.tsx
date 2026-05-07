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
import { fetchStockMovement } from './lib/graphql-client';

const rootRoute = createRootRoute({ component: RootLayout });

function RootLayout() {
  return (
    <AppShell brand="3PL" nav={[{ to: '/', label: 'ダッシュボード' }]}>
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
    queryKey: ['stockMovement', '1'],
    queryFn: () => fetchStockMovement('1'),
  });

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">入出庫ダッシュボード</h1>
        <p className="text-sm text-muted-foreground">
          bff-tpl 経由で StockMovement を取得しています。
        </p>
      </div>

      {isLoading && <p className="text-muted-foreground">読み込み中...</p>}
      {error && (
        <p className="rounded-lg border border-border bg-muted p-4 text-sm text-muted-foreground">
          BFF 取得失敗: {error instanceof Error ? error.message : String(error)}
        </p>
      )}

      {data?.stockMovement && (
        <section className="space-y-3 rounded-lg border border-border p-4">
          <h2 className="text-lg font-semibold">
            Movement {data.stockMovement.code} (id: {data.stockMovement.id})
          </h2>
          <dl className="grid grid-cols-2 gap-2 text-sm">
            <dt className="text-muted-foreground">取引先</dt>
            <dd>{data.stockMovement.partnerCode}</dd>
            <dt className="text-muted-foreground">SKU</dt>
            <dd>{data.stockMovement.skuCode}</dd>
            <dt className="text-muted-foreground">Location</dt>
            <dd>{data.stockMovement.locationId}</dd>
            <dt className="text-muted-foreground">種別</dt>
            <dd>{data.stockMovement.movementType}</dd>
            <dt className="text-muted-foreground">数量</dt>
            <dd>{data.stockMovement.quantity.toLocaleString()}</dd>
            <dt className="text-muted-foreground">ステータス</dt>
            <dd>{data.stockMovement.status}</dd>
            <dt className="text-muted-foreground">参照コード</dt>
            <dd>{data.stockMovement.referenceCode ?? '-'}</dd>
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
