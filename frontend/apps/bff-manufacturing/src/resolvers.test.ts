import { describe, expect, it } from 'vitest';

import { createLoaders } from './dataloaders.js';
import { resolvers, type BffContext } from './resolvers.js';

describe('Query.health', () => {
  it('returns "ok"', () => {
    expect(resolvers.Query.health()).toBe('ok');
  });
});

describe('Query.workOrder', () => {
  it('DataLoader 経由で in-memory mock を返す', async () => {
    const ctx: BffContext = { loaders: createLoaders(), authToken: null };
    const wo = await resolvers.Query.workOrder(undefined, { workOrderId: 'WO-1' }, ctx);
    expect(wo.workOrderId).toBe('WO-1');
    expect(wo.status).toBe('STARTED');
  });
});
