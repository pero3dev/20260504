import DataLoader from 'dataloader';

export interface SalesOrderSnapshot {
  salesOrderId: string;
  partnerId: string;
  status: 'PLACED' | 'RESERVED' | 'SHIPPED' | 'COMPLETED' | 'CANCELLED';
  totalAmountJpy: number;
  placedAt: string;
  shippedAt: string | null;
}

export function createSalesOrderLoader() {
  return new DataLoader<string, SalesOrderSnapshot>(async (ids) => {
    return ids.map((id) => ({
      salesOrderId: id,
      partnerId: 'PTNR-1',
      status: 'PLACED' as const,
      totalAmountJpy: 120_000,
      placedAt: new Date().toISOString(),
      shippedAt: null,
    }));
  });
}

export interface DataLoaderContext {
  salesOrderById: ReturnType<typeof createSalesOrderLoader>;
}

export function createLoaders(): DataLoaderContext {
  return {
    salesOrderById: createSalesOrderLoader(),
  };
}
