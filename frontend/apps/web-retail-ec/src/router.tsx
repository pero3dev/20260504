import { AppShell, AuthButtons, OidcCallbackPage } from '@inventory/ui';
import { LineChart } from '@inventory/ui/charts';
import { useQuery } from '@tanstack/react-query';
import {
  createRootRoute,
  createRoute,
  createRouter,
  Outlet,
  useNavigate,
} from '@tanstack/react-router';
import { useTranslation } from 'react-i18next';

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

// time-series API が未実装のため mock。 phase 3 で inventory-read-model に
// `/v1/inventories/{id}/trend?from=...&to=...` を追加 → BFF schema に展開して差替。
const SAMPLE_TREND = [
  { date: '5/2', available: 95 },
  { date: '5/3', available: 92 },
  { date: '5/4', available: 90 },
  { date: '5/5', available: 88 },
  { date: '5/6', available: 100 },
  { date: '5/7', available: 98 },
  { date: '5/8', available: 96 },
];

function DashboardPage() {
  const { t } = useTranslation('retail-ec');
  const { t: tCommon } = useTranslation('common');
  const { data, isLoading, error } = useQuery({
    queryKey: ['inventory', DEFAULT_INVENTORY_ID],
    queryFn: () => fetchInventory(DEFAULT_INVENTORY_ID),
  });

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">{t('dashboard.title')}</h1>
        <p className="text-sm text-muted-foreground">{t('dashboard.description')}</p>
      </div>

      {isLoading && <p className="text-muted-foreground">{tCommon('ui.loading')}</p>}
      {error && (
        <p className="rounded-lg border border-border bg-muted p-4 text-sm text-muted-foreground">
          {t('dashboard.fetch_failed', {
            message: error instanceof Error ? error.message : String(error),
          })}
        </p>
      )}

      {data?.inventory === null && !isLoading && (
        <p className="rounded-lg border border-border bg-muted p-4 text-sm text-muted-foreground">
          {t('dashboard.not_found', { inventoryId: DEFAULT_INVENTORY_ID })}
        </p>
      )}

      {data?.inventory && (
        <section className="space-y-3 rounded-lg border border-border p-4">
          <h2 className="text-lg font-semibold">
            {t('dashboard.card_heading', { id: data.inventory.id })}
          </h2>
          <dl className="grid grid-cols-2 gap-2 text-sm">
            <dt className="text-muted-foreground">{t('dashboard.fields.sku')}</dt>
            <dd>{data.inventory.skuId}</dd>
            <dt className="text-muted-foreground">{t('dashboard.fields.location')}</dt>
            <dd>{data.inventory.locationId}</dd>
            <dt className="text-muted-foreground">{t('dashboard.fields.available')}</dt>
            <dd>{data.inventory.available}</dd>
            <dt className="text-muted-foreground">{t('dashboard.fields.reserved')}</dt>
            <dd>{data.inventory.reserved}</dd>
            <dt className="text-muted-foreground">{t('dashboard.fields.version')}</dt>
            <dd>{data.inventory.version}</dd>
          </dl>
        </section>
      )}

      <section className="space-y-3 rounded-lg border border-border p-4">
        <header>
          <h2 className="text-lg font-semibold">{t('dashboard.trend.title')}</h2>
          <p className="text-xs text-muted-foreground">{t('dashboard.trend.subtitle')}</p>
        </header>
        <LineChart
          data={SAMPLE_TREND}
          xKey="date"
          series={[{ dataKey: 'available', label: t('dashboard.trend.series_available') }]}
          height={240}
        />
      </section>
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
