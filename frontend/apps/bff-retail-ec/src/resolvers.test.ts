import type { BffUserClaims } from '@inventory/shared';
import { describe, expect, it, vi } from 'vitest';

import {
  InventoryReadModelClient,
  type InventoryDto,
} from './clients/inventory-read-model-client.js';
import { createLoaders } from './dataloaders.js';
import { resolvers, type BffContext } from './resolvers.js';

describe('Query.health', () => {
  it('returns "ok"', () => {
    expect(resolvers.Query.health()).toBe('ok');
  });
});

describe('Query.inventory', () => {
  it('DataLoader 経由で client.getInventory を呼ぶ', async () => {
    const dto: InventoryDto = {
      id: 100,
      skuId: 'SKU-1',
      locationId: 'tokyo-warehouse',
      available: 50,
      reserved: 3,
      version: 1,
    };
    const client = new InventoryReadModelClient('http://stub');
    const spy = vi.spyOn(client, 'getInventory').mockResolvedValue(dto);
    const ctx: BffContext = {
      loaders: createLoaders(client, null),
      authToken: null,
      user: null,
    };

    const result = await resolvers.Query.inventory(undefined, { inventoryId: '100' }, ctx);

    expect(result).toEqual(dto);
    expect(spy).toHaveBeenCalledWith(100, null);
  });

  it('client が null を返したら resolver も null を返す', async () => {
    const client = new InventoryReadModelClient('http://stub');
    vi.spyOn(client, 'getInventory').mockResolvedValue(null);
    const ctx: BffContext = {
      loaders: createLoaders(client, null),
      authToken: null,
      user: null,
    };

    const result = await resolvers.Query.inventory(undefined, { inventoryId: '999' }, ctx);

    expect(result).toBeNull();
  });
});

describe('Query.viewer', () => {
  const baseCtx = (user: BffUserClaims | null): BffContext => ({
    loaders: createLoaders(new InventoryReadModelClient('http://stub'), null),
    authToken: null,
    user,
  });

  it('context.user 有り → Viewer を返す', () => {
    const claims: BffUserClaims = {
      userId: 42,
      tenantId: 'tenant-acme',
      roles: ['ROLE_USER'],
      scopes: { locations: ['tokyo'], partners: ['p-1'] },
      mfaStrength: 'low',
      locale: 'en',
    };
    const result = resolvers.Query.viewer(undefined, undefined, baseCtx(claims));
    expect(result).toEqual({
      userId: '42',
      tenantId: 'tenant-acme',
      roles: ['ROLE_USER'],
      locale: 'en',
      locations: ['tokyo'],
      partners: ['p-1'],
    });
  });

  it('未認証(context.user=null)→ null', () => {
    expect(resolvers.Query.viewer(undefined, undefined, baseCtx(null))).toBeNull();
  });
});
