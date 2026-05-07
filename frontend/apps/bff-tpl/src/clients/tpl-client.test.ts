import { afterEach, describe, expect, it, vi } from 'vitest';

import { TplClient, type StockMovementDto } from './tpl-client.js';

describe('TplClient.getStockMovement', () => {
  const client = new TplClient('http://localhost:8086');
  const dto: StockMovementDto = {
    id: 7,
    code: 'SM-1',
    partnerCode: 'PT-ACME',
    skuCode: 'SKU-A',
    locationId: 'tokyo-warehouse',
    movementType: 'INBOUND',
    quantity: 50,
    status: 'PLANNED',
    version: 1,
  };

  afterEach(() => vi.restoreAllMocks());

  it('200 で StockMovementDto を返す', async () => {
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

    expect(await client.getStockMovement(7, 'jwt-1')).toEqual(dto);
    expect(fetch).toHaveBeenCalledWith(
      'http://localhost:8086/v1/stock-movements/7',
      expect.objectContaining({
        headers: expect.objectContaining({ authorization: 'Bearer jwt-1' }),
      }),
    );
  });

  it('404 で null', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response('', { status: 404 })));
    expect(await client.getStockMovement(99, null)).toBeNull();
  });

  it('500 で Error', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response('boom', { status: 500 })));
    await expect(client.getStockMovement(1, null)).rejects.toThrow(/500/);
  });
});
