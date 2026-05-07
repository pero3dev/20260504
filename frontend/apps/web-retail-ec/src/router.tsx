import { useQuery } from '@tanstack/react-query';
import {
  createRootRoute,
  createRoute,
  createRouter,
  Link,
  Outlet,
} from '@tanstack/react-router';

import { fetchSku } from './lib/graphql-client';

// F1 vertical spike では file-based router を使わず手動 createRoute で 1 ファイル集約。
// F4 で per-business UI が増えてきたら TanStack Router の vite plugin で file-based に移行。

const rootRoute = createRootRoute({
  component: RootLayout,
});

function RootLayout() {
  return (
    <div className="min-h-screen bg-background">
      <header className="border-b border-border bg-background/95 backdrop-blur">
        <div className="mx-auto flex max-w-6xl items-center justify-between px-6 py-3">
          <Link to="/" className="text-lg font-semibold">
            Retail/EC
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
    queryKey: ['sku', 'SKU-1'],
    queryFn: () => fetchSku('SKU-1'),
  });

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">在庫ダッシュボード</h1>
        <p className="text-sm text-muted-foreground">
          F1 vertical spike — BFF(/graphql)経由で SKU 在庫を取得しています。
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
          <table className="w-full overflow-hidden rounded-lg border border-border text-sm">
            <thead className="bg-muted text-left">
              <tr>
                <th className="px-4 py-2 font-medium">Location</th>
                <th className="px-4 py-2 font-medium">Available</th>
                <th className="px-4 py-2 font-medium">Reserved</th>
                <th className="px-4 py-2 font-medium">Updated</th>
              </tr>
            </thead>
            <tbody>
              {data.sku.inventories.map((inv) => (
                <tr key={inv.locationId} className="border-t border-border">
                  <td className="px-4 py-2">{inv.locationId}</td>
                  <td className="px-4 py-2">{inv.available}</td>
                  <td className="px-4 py-2">{inv.reserved}</td>
                  <td className="px-4 py-2 text-muted-foreground">
                    {new Date(inv.updatedAt).toLocaleString('ja-JP')}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
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
