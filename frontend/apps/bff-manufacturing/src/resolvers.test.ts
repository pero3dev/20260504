import { describe, expect, it, vi } from 'vitest';

import { ManufacturingClient, type WorkOrderDto } from './clients/manufacturing-client.js';
import { createLoaders } from './dataloaders.js';
import { resolvers, type BffContext } from './resolvers.js';

describe('Query.health', () => {
  it('returns "ok"', () => {
    expect(resolvers.Query.health()).toBe('ok');
  });
});

describe('Query.workOrder', () => {
  it('DataLoader 経由で client.getWorkOrder を呼ぶ', async () => {
    const dto: WorkOrderDto = {
      id: 100,
      code: 'WO-2026-0001',
      productSkuCode: 'SKU-FINISHED-1',
      locationId: 'tokyo',
      plannedQuantity: 50,
      status: 'PLANNED',
      version: 1,
    };
    const client = new ManufacturingClient('http://stub');
    const spy = vi.spyOn(client, 'getWorkOrder').mockResolvedValue(dto);
    const ctx: BffContext = {
      loaders: createLoaders(client, null),
      authToken: null,
      user: null,
    };

    const result = await resolvers.Query.workOrder(undefined, { workOrderId: '100' }, ctx);

    expect(result).toEqual(dto);
    expect(spy).toHaveBeenCalledWith(100, null);
  });

  it('client が null を返したら resolver も null', async () => {
    const client = new ManufacturingClient('http://stub');
    vi.spyOn(client, 'getWorkOrder').mockResolvedValue(null);
    const ctx: BffContext = {
      loaders: createLoaders(client, null),
      authToken: null,
      user: null,
    };

    const result = await resolvers.Query.workOrder(undefined, { workOrderId: '999' }, ctx);

    expect(result).toBeNull();
  });
});
