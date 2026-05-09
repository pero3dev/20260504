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
import { fetchViewer, fetchWorkOrder } from './lib/graphql-client';

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

const DEFAULT_WORK_ORDER_ID = '1';

const SAMPLE_TREND = [
  { date: '5/2', completed: 8 },
  { date: '5/3', completed: 12 },
  { date: '5/4', completed: 6 },
  { date: '5/5', completed: 14 },
  { date: '5/6', completed: 11 },
  { date: '5/7', completed: 15 },
  { date: '5/8', completed: 10 },
];

interface FilterValues {
  workOrderId: string;
}

function DashboardPage() {
  const { t } = useTranslation('manufacturing');
  const { t: tCommon } = useTranslation('common');
  const [activeId, setActiveId] = useState<string>(DEFAULT_WORK_ORDER_ID);

  const filterSchema = z.object({
    workOrderId: z
      .string()
      .min(1, t('dashboard.filter.validation.required'))
      .regex(/^\d+$/, t('dashboard.filter.validation.numeric')),
  });
  const form = useForm<FilterValues>({
    resolver: zodResolver(filterSchema),
    defaultValues: { workOrderId: DEFAULT_WORK_ORDER_ID },
  });

  const { data, isLoading, error } = useQuery({
    queryKey: ['workOrder', activeId],
    queryFn: () => fetchWorkOrder(activeId),
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
          onSubmit={(values) => setActiveId(values.workOrderId)}
          className="flex items-end gap-3"
        >
          <div className="flex-1">
            <FormField<FilterValues, 'workOrderId'>
              name="workOrderId"
              label={t('dashboard.filter.work_order_id_label')}
              description={t('dashboard.filter.work_order_id_description')}
              render={({ field }) => (
                <input
                  id="workOrderId"
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

      {data?.workOrder && (
        <section className="space-y-3 rounded-lg border border-border p-4">
          <h2 className="text-lg font-semibold">
            {t('dashboard.card_heading', {
              code: data.workOrder.code,
              id: data.workOrder.id,
            })}
          </h2>
          <dl className="grid grid-cols-2 gap-2 text-sm">
            <dt className="text-muted-foreground">{t('dashboard.fields.product_sku')}</dt>
            <dd>{data.workOrder.productSkuCode}</dd>
            <dt className="text-muted-foreground">{t('dashboard.fields.location')}</dt>
            <dd>{data.workOrder.locationId}</dd>
            <dt className="text-muted-foreground">{t('dashboard.fields.planned_quantity')}</dt>
            <dd>{data.workOrder.plannedQuantity.toLocaleString()}</dd>
            <dt className="text-muted-foreground">{t('dashboard.fields.status')}</dt>
            <dd>{data.workOrder.status}</dd>
            <dt className="text-muted-foreground">{t('dashboard.fields.planned_start_date')}</dt>
            <dd>{data.workOrder.plannedStartDate ?? '-'}</dd>
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
          series={[{ dataKey: 'completed', label: t('dashboard.trend.series_completed') }]}
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
