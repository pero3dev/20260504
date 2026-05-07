import DataLoader from 'dataloader';

import { ManufacturingClient, type WorkOrderDto } from './clients/manufacturing-client.js';

/**
 * WorkOrder id → snapshot の DataLoader(F6 phase 2)。 同 id が 1 リクエスト内で複数参照されても
 * manufacturing service へは 1 回だけ叩く(CLAUDE.md ルール)。
 */
export function createWorkOrderLoader(
  client: ManufacturingClient,
  authToken: string | null,
) {
  return new DataLoader<string, WorkOrderDto | null>(async (ids) => {
    const numericIds = ids.map((id) => Number(id));
    const results = await Promise.all(
      numericIds.map((id) => client.getWorkOrder(id, authToken)),
    );
    return results;
  });
}

export interface DataLoaderContext {
  workOrderById: ReturnType<typeof createWorkOrderLoader>;
}

export function createLoaders(
  client: ManufacturingClient,
  authToken: string | null,
): DataLoaderContext {
  return {
    workOrderById: createWorkOrderLoader(client, authToken),
  };
}
