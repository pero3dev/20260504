import { AppShell, AuthButtons, OidcCallbackPage } from '@inventory/ui';
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
  const { t } = useTranslation('wholesale');
  const { t: tCommon } = useTranslation('common');
  const { data, isLoading, error } = useQuery({
    queryKey: ['salesOrder', '1'],
    queryFn: () => fetchSalesOrder('1'),
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

      {data?.salesOrder && (
        <section className="space-y-4 rounded-lg border border-border p-4">
          <header>
            <h2 className="text-lg font-semibold">
              {t('dashboard.card_heading', {
                code: data.salesOrder.code,
                id: data.salesOrder.id,
              })}
            </h2>
          </header>
          <dl className="grid grid-cols-2 gap-2 text-sm">
            <dt className="text-muted-foreground">{t('dashboard.fields.partner')}</dt>
            <dd>{data.salesOrder.partnerCode}</dd>
            <dt className="text-muted-foreground">{t('dashboard.fields.status')}</dt>
            <dd>{data.salesOrder.status}</dd>
            <dt className="text-muted-foreground">{t('dashboard.fields.currency')}</dt>
            <dd>{data.salesOrder.currency}</dd>
            <dt className="text-muted-foreground">{t('dashboard.fields.total_amount')}</dt>
            <dd>
              {data.salesOrder.totalAmount.toLocaleString('ja-JP')} {data.salesOrder.currency}
            </dd>
            <dt className="text-muted-foreground">
              {t('dashboard.fields.requested_delivery_date')}
            </dt>
            <dd>{data.salesOrder.requestedDeliveryDate ?? '-'}</dd>
          </dl>

          <div className="space-y-1">
            <h3 className="text-sm font-semibold">{t('dashboard.items.title')}</h3>
            <ul className="text-sm">
              {data.salesOrder.items.map((line, idx) => (
                <li
                  key={`${line.skuCode}-${line.locationId}-${idx}`}
                  className="border-t border-border py-1"
                >
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
