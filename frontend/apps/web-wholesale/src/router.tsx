import { zodResolver } from '@hookform/resolvers/zod';
import { useApplyTenantLocale } from '@inventory/shared/i18n';
import { AppShell, AuthButtons, Form, FormField, OidcCallbackPage } from '@inventory/ui';
import { LineChart } from '@inventory/ui/charts';
import { useQuery } from '@tanstack/react-query';
import {
  createRootRoute,
  createRoute,
  createRouter,
  Outlet,
  useNavigate,
} from '@tanstack/react-router';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { z } from 'zod';

import { authManager } from './lib/auth';
import { fetchSalesOrder, fetchViewer } from './lib/graphql-client';

const rootRoute = createRootRoute({ component: RootLayout });

function RootLayout() {
  const { data: viewerData } = useQuery({
    queryKey: ['viewer'],
    queryFn: fetchViewer,
    staleTime: Infinity,
    retry: false,
  });
  useApplyTenantLocale(viewerData?.viewer?.locale);

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

const DEFAULT_ORDER_ID = '1';

const SAMPLE_TREND = [
  { date: '5/2', revenue: 1_200_000 },
  { date: '5/3', revenue: 980_000 },
  { date: '5/4', revenue: 1_450_000 },
  { date: '5/5', revenue: 1_100_000 },
  { date: '5/6', revenue: 1_700_000 },
  { date: '5/7', revenue: 1_320_000 },
  { date: '5/8', revenue: 1_580_000 },
];

interface FilterValues {
  orderId: string;
}

function DashboardPage() {
  const { t } = useTranslation('wholesale');
  const { t: tCommon } = useTranslation('common');
  const [activeId, setActiveId] = useState<string>(DEFAULT_ORDER_ID);

  const filterSchema = z.object({
    orderId: z
      .string()
      .min(1, t('dashboard.filter.validation.required'))
      .regex(/^\d+$/, t('dashboard.filter.validation.numeric')),
  });
  const form = useForm<FilterValues>({
    resolver: zodResolver(filterSchema),
    defaultValues: { orderId: DEFAULT_ORDER_ID },
  });

  const { data, isLoading, error } = useQuery({
    queryKey: ['salesOrder', activeId],
    queryFn: () => fetchSalesOrder(activeId),
  });

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">{t('dashboard.title')}</h1>
        <p className="text-sm text-muted-foreground">{t('dashboard.description')}</p>
      </div>

      <section className="space-y-3 rounded-lg border border-border p-4">
        <h2 className="text-lg font-semibold">{t('dashboard.filter.title')}</h2>
        <Form
          form={form}
          onSubmit={(values) => setActiveId(values.orderId)}
          className="flex items-end gap-3"
        >
          <div className="flex-1">
            <FormField<FilterValues, 'orderId'>
              name="orderId"
              label={t('dashboard.filter.order_id_label')}
              description={t('dashboard.filter.order_id_description')}
              render={({ field }) => (
                <input
                  id="orderId"
                  type="text"
                  inputMode="numeric"
                  className="w-full rounded-md border border-border bg-background px-3 py-1 text-sm"
                  {...field}
                />
              )}
            />
          </div>
          <button
            type="submit"
            className="rounded-md bg-primary px-4 py-1 text-sm text-primary-foreground hover:opacity-90"
          >
            {t('dashboard.filter.fetch_button')}
          </button>
        </Form>
      </section>

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

      <section className="space-y-3 rounded-lg border border-border p-4">
        <header>
          <h2 className="text-lg font-semibold">{t('dashboard.trend.title')}</h2>
          <p className="text-xs text-muted-foreground">{t('dashboard.trend.subtitle')}</p>
        </header>
        <LineChart
          data={SAMPLE_TREND}
          xKey="date"
          series={[{ dataKey: 'revenue', label: t('dashboard.trend.series_revenue') }]}
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
