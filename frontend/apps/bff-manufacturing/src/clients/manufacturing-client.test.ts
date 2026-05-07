import { afterEach, describe, expect, it, vi } from 'vitest';

import { ManufacturingClient, type WorkOrderDto } from './manufacturing-client.js';

describe('ManufacturingClient.getWorkOrder', () => {
  const client = new ManufacturingClient('http://localhost:8088');
  const dto: WorkOrderDto = {
    id: 42,
    code: 'WO-1',
    productSkuCode: 'SKU-A',
    locationId: 'tokyo',
    plannedQuantity: 10,
    status: 'PLANNED',
    version: 1,
  };

  afterEach(() => vi.restoreAllMocks());

  it('200 で WorkOrderDto を返す', async () => {
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

    expect(await client.getWorkOrder(42, 'jwt-1')).toEqual(dto);
    expect(fetch).toHaveBeenCalledWith(
      'http://localhost:8088/v1/work-orders/42',
      expect.objectContaining({
        headers: expect.objectContaining({ authorization: 'Bearer jwt-1' }),
      }),
    );
  });

  it('404 で null', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response('', { status: 404 })));
    expect(await client.getWorkOrder(99, null)).toBeNull();
  });

  it('500 で Error', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response('boom', { status: 500 })));
    await expect(client.getWorkOrder(1, null)).rejects.toThrow(/500/);
  });
});
