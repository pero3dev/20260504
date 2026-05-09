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
import { fetchStockMovement, fetchViewer } from './lib/graphql-client';

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

const DEFAULT_MOVEMENT_ID = '1';

const SAMPLE_TREND = [
  { date: '5/2', inbound: 120, outbound: 80 },
  { date: '5/3', inbound: 90, outbound: 110 },
  { date: '5/4', inbound: 140, outbound: 60 },
  { date: '5/5', inbound: 70, outbound: 100 },
  { date: '5/6', inbound: 160, outbound: 90 },
  { date: '5/7', inbound: 100, outbound: 120 },
  { date: '5/8', inbound: 130, outbound: 95 },
];

interface FilterValues {
  movementId: string;
}

function DashboardPage() {
  const { t } = useTranslation('tpl');
  const { t: tCommon } = useTranslation('common');
  const [activeId, setActiveId] = useState<string>(DEFAULT_MOVEMENT_ID);

  const filterSchema = z.object({
    movementId: z
      .string()
      .min(1, t('dashboard.filter.validation.required'))
      .regex(/^\d+$/, t('dashboard.filter.validation.numeric')),
  });
  const form = useForm<FilterValues>({
    resolver: zodResolver(filterSchema),
    defaultValues: { movementId: DEFAULT_MOVEMENT_ID },
  });

  const { data, isLoading, error } = useQuery({
    queryKey: ['stockMovement', activeId],
    queryFn: () => fetchStockMovement(activeId),
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
          onSubmit={(values) => setActiveId(values.movementId)}
          className="flex items-end gap-3"
        >
          <div className="flex-1">
            <FormField<FilterValues, 'movementId'>
              name="movementId"
              label={t('dashboard.filter.movement_id_label')}
              description={t('dashboard.filter.movement_id_description')}
              render={({ field }) => (
                <input
                  id="movementId"
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

      {data?.stockMovement && (
        <section className="space-y-3 rounded-lg border border-border p-4">
          <h2 className="text-lg font-semibold">
            {t('dashboard.card_heading', {
              code: data.stockMovement.code,
              id: data.stockMovement.id,
            })}
          </h2>
          <dl className="grid grid-cols-2 gap-2 text-sm">
            <dt className="text-muted-foreground">{t('dashboard.fields.partner')}</dt>
            <dd>{data.stockMovement.partnerCode}</dd>
            <dt className="text-muted-foreground">{t('dashboard.fields.sku')}</dt>
            <dd>{data.stockMovement.skuCode}</dd>
            <dt className="text-muted-foreground">{t('dashboard.fields.location')}</dt>
            <dd>{data.stockMovement.locationId}</dd>
            <dt className="text-muted-foreground">{t('dashboard.fields.movement_type')}</dt>
            <dd>{data.stockMovement.movementType}</dd>
            <dt className="text-muted-foreground">{t('dashboard.fields.quantity')}</dt>
            <dd>{data.stockMovement.quantity.toLocaleString()}</dd>
            <dt className="text-muted-foreground">{t('dashboard.fields.status')}</dt>
            <dd>{data.stockMovement.status}</dd>
            <dt className="text-muted-foreground">{t('dashboard.fields.reference_code')}</dt>
            <dd>{data.stockMovement.referenceCode ?? '-'}</dd>
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
          series={[
            { dataKey: 'inbound', label: t('dashboard.trend.series_inbound') },
            { dataKey: 'outbound', label: t('dashboard.trend.series_outbound') },
          ]}
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
