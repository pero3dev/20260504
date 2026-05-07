import DataLoader from 'dataloader';

export interface WorkOrderSnapshot {
  workOrderId: string;
  productSkuId: string;
  status: 'STARTED' | 'RELEASED' | 'COMPLETED' | 'FAILED' | 'CANCELLED';
  startedAt: string;
  completedAt: string | null;
}

/**
 * WorkOrder id → snapshot の DataLoader(F3 mock 実装)。 F6 で実 manufacturing
 * service への HTTP 呼出に差替える。
 */
export function createWorkOrderLoader() {
  return new DataLoader<string, WorkOrderSnapshot>(async (ids) => {
    return ids.map((id) => ({
      workOrderId: id,
      productSkuId: 'SKU-FINISHED-1',
      status: 'STARTED' as const,
      startedAt: new Date().toISOString(),
      completedAt: null,
    }));
  });
}

export interface DataLoaderContext {
  workOrderById: ReturnType<typeof createWorkOrderLoader>;
}

export function createLoaders(): DataLoaderContext {
  return {
    workOrderById: createWorkOrderLoader(),
  };
}
