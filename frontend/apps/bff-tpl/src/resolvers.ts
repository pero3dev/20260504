import type { BffUserClaims } from '@inventory/shared';

import type { DataLoaderContext } from './dataloaders.js';

export interface BffContext {
  loaders: DataLoaderContext;
  authToken: string | null;
  user: BffUserClaims | null;
}

export const resolvers = {
  Query: {
    health: () => 'ok',
    stockMovement: async (
      _parent: unknown,
      args: { movementId: string },
      context: BffContext,
    ) => {
      return context.loaders.movementById.load(args.movementId);
    },
    viewer: (_parent: unknown, _args: unknown, context: BffContext) => {
      return context.user ? toViewer(context.user) : null;
    },
  },
};

function toViewer(claims: BffUserClaims) {
  return {
    userId: String(claims.userId),
    tenantId: claims.tenantId,
    roles: claims.roles,
    locale: claims.locale,
    locations: claims.scopes.locations,
    partners: claims.scopes.partners,
  };
}
