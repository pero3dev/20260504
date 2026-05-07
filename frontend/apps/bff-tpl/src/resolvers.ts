import type { DataLoaderContext } from './dataloaders.js';

export interface BffContext {
  loaders: DataLoaderContext;
  authToken: string | null;
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
