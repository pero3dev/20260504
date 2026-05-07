import DataLoader from 'dataloader';

import { WholesaleClient, type SalesOrderDto } from './clients/wholesale-client.js';

/**
 * SalesOrder id → snapshot の DataLoader(F6 phase 2)。 同 id が 1 リクエスト内で複数参照されても
 * wholesale service へは 1 回だけ叩く(CLAUDE.md ルール)。
 */
export function createSalesOrderLoader(
  client: WholesaleClient,
  authToken: string | null,
) {
  return new DataLoader<string, SalesOrderDto | null>(async (ids) => {
    const numericIds = ids.map((id) => Number(id));
    const results = await Promise.all(
      numericIds.map((id) => client.getSalesOrder(id, authToken)),
    );
    return results;
  });
}

export interface DataLoaderContext {
  salesOrderById: ReturnType<typeof createSalesOrderLoader>;
}

export function createLoaders(
  client: WholesaleClient,
  authToken: string | null,
): DataLoaderContext {
  return {
    salesOrderById: createSalesOrderLoader(client, authToken),
  };
}
