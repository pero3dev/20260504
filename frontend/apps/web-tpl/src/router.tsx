import { useQuery } from '@tanstack/react-query';
import {
  createRootRoute,
  createRoute,
  createRouter,
  Link,
  Outlet,
} from '@tanstack/react-router';

import { fetchStockMovement } from './lib/graphql-client';

const rootRoute = createRootRoute({ component: RootLayout });

function RootLayout() {
  return (
    <div className="min-h-screen bg-background">
      <header className="border-b border-border bg-background/95 backdrop-blur">
        <div className="mx-auto flex max-w-6xl items-center justify-between px-6 py-3">
          <Link to="/" className="text-lg font-semibold">
            3PL
          </Link>
          <nav className="flex gap-4 text-sm">
            <Link
              to="/"
              activeProps={{ className: 'font-semibold' }}
              className="text-muted-foreground hover:text-foreground"
            >
              ダッシュボード
            </Link>
          </nav>
        </div>
      </header>
      <main className="mx-auto max-w-6xl px-6 py-8">
        <Outlet />
      </main>
    </div>
  );
}

const indexRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/',
  component: DashboardPage,
});

function DashboardPage() {
  const { data, isLoading, error } = useQuery({
    queryKey: ['stockMovement', 'M-1'],
    queryFn: () => fetchStockMovement('M-1'),
  });

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">入出庫ダッシュボード</h1>
        <p className="text-sm text-muted-foreground">
          F3 vertical spike — bff-tpl 経由で StockMovement を取得しています。
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
          <h2 className="text-lg font-semibold">Movement {data.stockMovement.movementId}</h2>
          <dl className="grid grid-cols-2 gap-2 text-sm">
            <dt className="text-muted-foreground">SKU</dt>
            <dd>{data.stockMovement.skuId}</dd>
            <dt className="text-muted-foreground">Location</dt>
            <dd>{data.stockMovement.locationId}</dd>
            <dt className="text-muted-foreground">方向</dt>
            <dd>{data.stockMovement.direction}</dd>
            <dt className="text-muted-foreground">数量</dt>
            <dd>{data.stockMovement.quantity}</dd>
            <dt className="text-muted-foreground">発生時刻</dt>
            <dd>{new Date(data.stockMovement.occurredAt).toLocaleString('ja-JP')}</dd>
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
