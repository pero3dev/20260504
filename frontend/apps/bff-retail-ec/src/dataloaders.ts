import DataLoader from 'dataloader';

import {
  InventoryReadModelClient,
  type InventoryDto,
} from './clients/inventory-read-model-client.js';

/**
 * inventory id → InventoryDto | null の DataLoader(F6)。 1 リクエスト内で同 id が複数回参照されても
 * inventory-read-model HTTP 呼出を 1 回にまとめる(CLAUDE.md ルール:GraphQL は DataLoader 必須)。
 *
 * <p>本実装は順次呼び出し(`Promise.all`)。 inventory-read-model が batch GET を提供したら
 * `POST /v1/inventories/_batch` 経路に差し替える。
 */
export function createInventoryByIdLoader(
  client: InventoryReadModelClient,
  authToken: string | null,
) {
  return new DataLoader<string, InventoryDto | null>(async (ids) => {
    const numericIds = ids.map((id) => Number(id));
    const results = await Promise.all(
      numericIds.map((id) => client.getInventory(id, authToken)),
    );
    return results;
  });
}

export interface DataLoaderContext {
  inventoryById: ReturnType<typeof createInventoryByIdLoader>;
}

export function createLoaders(
  client: InventoryReadModelClient,
  authToken: string | null,
): DataLoaderContext {
  return {
    inventoryById: createInventoryByIdLoader(client, authToken),
  };
}
