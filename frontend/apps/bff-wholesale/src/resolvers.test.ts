import { describe, expect, it, vi } from 'vitest';

import { WholesaleClient, type SalesOrderDto } from './clients/wholesale-client.js';
import { createLoaders } from './dataloaders.js';
import { resolvers, type BffContext } from './resolvers.js';

describe('Query.health', () => {
  it('returns "ok"', () => {
    expect(resolvers.Query.health()).toBe('ok');
  });
});

describe('Query.salesOrder', () => {
  it('DataLoader 経由で client.getSalesOrder を呼ぶ', async () => {
    const dto: SalesOrderDto = {
      id: 11,
      code: 'SO-2026-0001',
      partnerCode: 'PARTNER-ACME',
      status: 'PLACED',
      currency: 'JPY',
      totalAmount: 12000,
      items: [
        { skuCode: 'SKU-A', locationId: 'LOC-TOKYO-A', quantity: 10, unitPrice: 1200 },
      ],
      version: 1,
    };
    const client = new WholesaleClient('http://stub');
    const spy = vi.spyOn(client, 'getSalesOrder').mockResolvedValue(dto);
    const ctx: BffContext = { loaders: createLoaders(client, null), authToken: null };

    const result = await resolvers.Query.salesOrder(undefined, { orderId: '11' }, ctx);

    expect(result).toEqual(dto);
    expect(spy).toHaveBeenCalledWith(11, null);
  });

  it('client が null を返したら resolver も null', async () => {
    const client = new WholesaleClient('http://stub');
    vi.spyOn(client, 'getSalesOrder').mockResolvedValue(null);
    const ctx: BffContext = { loaders: createLoaders(client, null), authToken: null };

    const result = await resolvers.Query.salesOrder(undefined, { orderId: '999' }, ctx);

    expect(result).toBeNull();
  });
});
