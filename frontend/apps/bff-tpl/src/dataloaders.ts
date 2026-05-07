import DataLoader from 'dataloader';

import type { TplClient, StockMovementDto } from './clients/tpl-client.js';

/**
 * StockMovement id → snapshot の DataLoader(F6 phase 2)。 同 id が 1 リクエスト内で複数参照されても
 * tpl service へは 1 回だけ叩く(CLAUDE.md ルール)。
 */
export function createStockMovementLoader(
  client: TplClient,
  authToken: string | null,
) {
  return new DataLoader<string, StockMovementDto | null>(async (ids) => {
    const numericIds = ids.map((id) => Number(id));
    const results = await Promise.all(
      numericIds.map((id) => client.getStockMovement(id, authToken)),
    );
    return results;
  });
}

export interface DataLoaderContext {
  movementById: ReturnType<typeof createStockMovementLoader>;
}

export function createLoaders(
  client: TplClient,
  authToken: string | null,
): DataLoaderContext {
  return {
    movementById: createStockMovementLoader(client, authToken),
  };
}
