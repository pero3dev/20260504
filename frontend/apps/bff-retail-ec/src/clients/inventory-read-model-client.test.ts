import { afterEach, describe, expect, it, vi } from 'vitest';

import {
  InventoryReadModelClient,
  type InventoryDto,
} from './inventory-read-model-client.js';

describe('InventoryReadModelClient.getInventory', () => {
  const client = new InventoryReadModelClient('http://localhost:8080');
  const dto: InventoryDto = {
    id: 42,
    skuId: 'SKU-A',
    locationId: 'tokyo',
    available: 10,
    reserved: 2,
    version: 1,
  };

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('200 レスポンスを InventoryDto として返す', async () => {
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

    const result = await client.getInventory(42, 'jwt-1');

    expect(result).toEqual(dto);
    expect(fetch).toHaveBeenCalledWith(
      'http://localhost:8080/v1/inventories/42',
      expect.objectContaining({
        headers: expect.objectContaining({
          authorization: 'Bearer jwt-1',
        }),
      }),
    );
  });

  it('404 で null を返す', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response('', { status: 404 })));

    const result = await client.getInventory(99, null);

    expect(result).toBeNull();
  });

  it('500 で Error を投げる', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => new Response('boom', { status: 500 })),
    );

    await expect(client.getInventory(1, null)).rejects.toThrow(/500/);
  });
});
