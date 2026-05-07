import { describe, expect, it } from 'vitest';

import { createLoaders } from './dataloaders.js';
import { resolvers, type BffContext } from './resolvers.js';

describe('Query.health', () => {
  it('returns "ok"', () => {
    expect(resolvers.Query.health()).toBe('ok');
  });
});

describe('Query.stockMovement', () => {
  it('DataLoader 経由で in-memory mock を返す', async () => {
    const ctx: BffContext = { loaders: createLoaders(), authToken: null };
    const m = await resolvers.Query.stockMovement(undefined, { movementId: 'M-1' }, ctx);
    expect(m.movementId).toBe('M-1');
    expect(m.direction).toBe('IN');
  });
});
