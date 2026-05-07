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
  },
};
