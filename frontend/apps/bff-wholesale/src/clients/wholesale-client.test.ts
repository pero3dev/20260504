import { afterEach, describe, expect, it, vi } from 'vitest';

import { WholesaleClient, type SalesOrderDto } from './wholesale-client.js';

describe('WholesaleClient.getSalesOrder', () => {
  const client = new WholesaleClient('http://localhost:8087');
  const dto: SalesOrderDto = {
    id: 11,
    code: 'SO-1',
    partnerCode: 'PARTNER-ACME',
    status: 'PLACED',
    currency: 'JPY',
    totalAmount: 12000,
    items: [
      { skuCode: 'SKU-A', locationId: 'tokyo', quantity: 10, unitPrice: 1200 },
    ],
    version: 1,
  };

  afterEach(() => vi.restoreAllMocks());

  it('200 で SalesOrderDto を返す', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(
        async () =>
          new Response(JSON.stringify(dto), {
            status: 200,
            headers: { 'content-type': 'application/json' },
          }),
      ),
    );

    expect(await client.getSalesOrder(11, 'jwt-1')).toEqual(dto);
    expect(fetch).toHaveBeenCalledWith(
      'http://localhost:8087/v1/sales-orders/11',
      expect.objectContaining({
        headers: expect.objectContaining({ authorization: 'Bearer jwt-1' }),
      }),
    );
  });

  it('404 で null', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response('', { status: 404 })));
    expect(await client.getSalesOrder(99, null)).toBeNull();
  });

  it('500 で Error', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response('boom', { status: 500 })));
    await expect(client.getSalesOrder(1, null)).rejects.toThrow(/500/);
  });
});
