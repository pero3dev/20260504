import DataLoader from 'dataloader';

import type { InventorySnapshot } from '@inventory/shared';

/**
 * SKU id → InventorySnapshot[] の DataLoader。 1 リクエスト内で同 SKU が複数回参照されても backend(将来は
 * inventory-read-model)への呼出を 1 回にまとめる(CLAUDE.md ルール:GraphQL は DataLoader 必須)。
 *
 * <p>F1 vertical spike では in-memory mock を返す。 F6 で実 backend HTTP 呼出に差し替える。
 */
export function createInventoryByLoader() {
  return new DataLoader<string, InventorySnapshot[]>(async (skuIds) => {
    // Mock 実装。 実装は inventory-read-model API を `Promise.all` で叩く想定。
    return skuIds.map((skuId) => [
      {
        tenantId: 'dev',
        skuId,
        locationId: 'tokyo-warehouse',
        available: 100,
        reserved: 5,
        updatedAt: new Date().toISOString(),
      },
    ]);
  });
}

/** 1 リクエスト 1 セットの DataLoader 群を context として供給する。 */
export interface DataLoaderContext {
  inventoryBySku: ReturnType<typeof createInventoryByLoader>;
}

export function createLoaders(): DataLoaderContext {
  return {
    inventoryBySku: createInventoryByLoader(),
  };
}
