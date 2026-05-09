import type { BffUserClaims } from '@inventory/shared';
import { describe, expect, it, vi } from 'vitest';

import { TplClient, type StockMovementDto } from './clients/tpl-client.js';
import { createLoaders } from './dataloaders.js';
import { resolvers, type BffContext } from './resolvers.js';

describe('Query.health', () => {
  it('returns "ok"', () => {
    expect(resolvers.Query.health()).toBe('ok');
  });
});

describe('Query.stockMovement', () => {
  it('DataLoader 経由で client.getStockMovement を呼ぶ', async () => {
    const dto: StockMovementDto = {
      id: 7,
      code: 'SM-2026-0001',
      partnerCode: 'PT-ACME',
      skuCode: 'SKU-COCA-COLA-500ML',
      locationId: 'LOC-WAREHOUSE-EAST',
      movementType: 'INBOUND',
      quantity: 50,
      status: 'PLANNED',
      version: 1,
    };
    const client = new TplClient('http://stub');
    const spy = vi.spyOn(client, 'getStockMovement').mockResolvedValue(dto);
    const ctx: BffContext = {
      loaders: createLoaders(client, null),
      authToken: null,
      user: null,
    };

    const result = await resolvers.Query.stockMovement(undefined, { movementId: '7' }, ctx);

    expect(result).toEqual(dto);
    expect(spy).toHaveBeenCalledWith(7, null);
  });

  it('client が null を返したら resolver も null', async () => {
    const client = new TplClient('http://stub');
    vi.spyOn(client, 'getStockMovement').mockResolvedValue(null);
    const ctx: BffContext = {
      loaders: createLoaders(client, null),
      authToken: null,
      user: null,
    };

    const result = await resolvers.Query.stockMovement(undefined, { movementId: '999' }, ctx);

    expect(result).toBeNull();
  });
});

describe('Query.viewer', () => {
  const baseCtx = (user: BffUserClaims | null): BffContext => ({
    loaders: createLoaders(new TplClient('http://stub'), null),
    authToken: null,
    user,
  });

  it('context.user 有り → Viewer を返す', () => {
    const claims: BffUserClaims = {
      userId: 9,
      tenantId: 'tenant-3pl',
      roles: ['ROLE_OPS'],
      scopes: { locations: ['LOC-WAREHOUSE-EAST'], partners: ['PT-ACME'] },
      mfaStrength: 'low',
      locale: 'ja',
    };
    const result = resolvers.Query.viewer(undefined, undefined, baseCtx(claims));
    expect(result).toEqual({
      userId: '9',
      tenantId: 'tenant-3pl',
      roles: ['ROLE_OPS'],
      locale: 'ja',
      locations: ['LOC-WAREHOUSE-EAST'],
      partners: ['PT-ACME'],
    });
  });

  it('未認証(context.user=null)→ null', () => {
    expect(resolvers.Query.viewer(undefined, undefined, baseCtx(null))).toBeNull();
  });
});
