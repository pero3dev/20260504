import {
  createI18n,
  defaultResources,
  mergeResources,
  retailEcResources,
} from '@inventory/shared/i18n';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { RouterProvider } from '@tanstack/react-router';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { I18nextProvider } from 'react-i18next';

import { router } from './router';

import '@inventory/ui/styles.css';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      retry: 1,
    },
  },
});

const rootElement = document.getElementById('root');
if (!rootElement) throw new Error('#root element not found');

// MVP は ja 固定。 phase 3 で Identity Broker tenant.locale claim を読んで切替予定。
// top-level await を避け esbuild es2020 target でも build できるように .then() で render を遅延。
void createI18n({
  language: 'ja',
  resources: mergeResources(defaultResources, retailEcResources),
}).then((i18n) => {
  createRoot(rootElement).render(
    <StrictMode>
      <I18nextProvider i18n={i18n}>
        <QueryClientProvider client={queryClient}>
          <RouterProvider router={router} />
        </QueryClientProvider>
      </I18nextProvider>
    </StrictMode>,
  );
});
