import { describe, expect, it } from 'vitest';

import { createLoaders } from './dataloaders.js';
import { resolvers, type BffContext } from './resolvers.js';

describe('Query.health', () => {
  it('returns "ok"', () => {
    expect(resolvers.Query.health()).toBe('ok');
  });
});

describe('Query.salesOrder', () => {
  it('DataLoader 経由で in-memory mock を返す', async () => {
    const ctx: BffContext = { loaders: createLoaders(), authToken: null };
    const so = await resolvers.Query.salesOrder(undefined, { salesOrderId: 'SO-1' }, ctx);
    expect(so.salesOrderId).toBe('SO-1');
    expect(so.status).toBe('PLACED');
  });
});
