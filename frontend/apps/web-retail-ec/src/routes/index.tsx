import { useQuery } from '@tanstack/react-query';
import { createFileRoute } from '@tanstack/react-router';

import { fetchSku } from '@/lib/graphql-client';

export const Route = createFileRoute('/')({
  component: DashboardPage,
});

function DashboardPage() {
  // F1 では SKU id を固定で叩く。 F4 で UI controls + form 経由に進化。
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
