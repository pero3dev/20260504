import DataLoader from 'dataloader';

export interface StockMovementSnapshot {
  movementId: string;
  skuId: string;
  locationId: string;
  direction: 'IN' | 'OUT';
  quantity: number;
  occurredAt: string;
}

export function createStockMovementLoader() {
  return new DataLoader<string, StockMovementSnapshot>(async (ids) => {
    return ids.map((id) => ({
      movementId: id,
      skuId: 'SKU-A',
      locationId: 'tokyo-warehouse',
      direction: 'IN' as const,
      quantity: 50,
      occurredAt: new Date().toISOString(),
    }));
  });
}

export interface DataLoaderContext {
  movementById: ReturnType<typeof createStockMovementLoader>;
}

export function createLoaders(): DataLoaderContext {
  return {
    movementById: createStockMovementLoader(),
  };
}
