import { describe, expect, it } from 'vitest';

import { createLoaders } from './dataloaders.js';
import { resolvers, type BffContext } from './resolvers.js';

describe('Query.health', () => {
  it('returns "ok"', () => {
    expect(resolvers.Query.health()).toBe('ok');
  });
});

describe('Query.sku → Sku.inventories', () => {
  it('DataLoader 経由で in-memory mock を返す', async () => {
    const ctx: BffContext = { loaders: createLoaders(), authToken: null };
    const sku = resolvers.Query.sku(undefined, { skuId: 'SKU-1' });
    const inventories = await resolvers.Sku.inventories({ skuId: sku.skuId }, undefined, ctx);

    expect(inventories).toHaveLength(1);
    expect(inventories[0]?.skuId).toBe('SKU-1');
    expect(inventories[0]?.available).toBe(100);
  });
});
