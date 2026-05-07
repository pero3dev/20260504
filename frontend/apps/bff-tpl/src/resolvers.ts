import { GraphQLScalarType, Kind } from 'graphql';

import type { DataLoaderContext } from './dataloaders.js';

const DateTime = new GraphQLScalarType<string, string>({
  name: 'DateTime',
  description: 'ISO 8601 timestamp(UTC)',
  serialize(value) {
    if (value instanceof Date) return value.toISOString();
    if (typeof value === 'string') return value;
    throw new TypeError('DateTime serialize: ISO string か Date が必要');
  },
  parseValue(value) {
    if (typeof value !== 'string') throw new TypeError('DateTime parseValue: string が必要');
    return value;
  },
  parseLiteral(ast) {
    if (ast.kind !== Kind.STRING) throw new TypeError('DateTime parseLiteral: string literal が必要');
    return ast.value;
  },
});

export interface BffContext {
  loaders: DataLoaderContext;
  authToken: string | null;
}

export const resolvers = {
  DateTime,
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
