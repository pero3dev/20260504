// このファイルは TanStack Router の vite plugin が自動生成する。
// pnpm install + pnpm dev / pnpm build を 1 度走らせると src/routes/ から regen される。
// F1 commit 時点の placeholder として最小コードを書いておく(plugin が稼働すれば上書きされる)。

/* eslint-disable */
// @ts-nocheck

import { createRoute, lazyRouteComponent } from '@tanstack/react-router';
import { Route as RootRoute } from './routes/__root';

const IndexRoute = createRoute({
  getParentRoute: () => RootRoute,
  path: '/',
  component: lazyRouteComponent(() => import('./routes/index.tsx').then((m) => ({ default: m.Route.options.component })),
  ),
});

export const routeTree = RootRoute.addChildren([IndexRoute]);
