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
import { fetchSalesOrder } from './lib/graphql-client';

const rootRoute = createRootRoute({ component: RootLayout });

function RootLayout() {
  return (
    <AppShell brand="Wholesale" nav={[{ to: '/', label: 'ダッシュボード' }]}>
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
    queryKey: ['salesOrder', '1'],
    queryFn: () => fetchSalesOrder('1'),
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
        <section className="space-y-4 rounded-lg border border-border p-4">
          <header>
            <h2 className="text-lg font-semibold">
              SalesOrder {data.salesOrder.code} (id: {data.salesOrder.id})
            </h2>
          </header>
          <dl className="grid grid-cols-2 gap-2 text-sm">
            <dt className="text-muted-foreground">取引先</dt>
            <dd>{data.salesOrder.partnerCode}</dd>
            <dt className="text-muted-foreground">ステータス</dt>
            <dd>{data.salesOrder.status}</dd>
            <dt className="text-muted-foreground">通貨</dt>
            <dd>{data.salesOrder.currency}</dd>
            <dt className="text-muted-foreground">合計金額</dt>
            <dd>
              {data.salesOrder.totalAmount.toLocaleString('ja-JP')} {data.salesOrder.currency}
            </dd>
            <dt className="text-muted-foreground">納期希望</dt>
            <dd>{data.salesOrder.requestedDeliveryDate ?? '-'}</dd>
          </dl>

          <div className="space-y-1">
            <h3 className="text-sm font-semibold">明細</h3>
            <ul className="text-sm">
              {data.salesOrder.items.map((line, idx) => (
                <li key={`${line.skuCode}-${line.locationId}-${idx}`} className="border-t border-border py-1">
                  {line.skuCode} @ {line.locationId} — {line.quantity} ×{' '}
                  {line.unitPrice.toLocaleString('ja-JP')} {data.salesOrder!.currency}
                </li>
              ))}
            </ul>
          </div>
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
