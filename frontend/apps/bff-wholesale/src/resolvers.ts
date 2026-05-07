import { GraphQLScalarType, Kind } from 'graphql';

import type { DataLoaderContext } from './dataloaders.js';

const Date_ = new GraphQLScalarType<string, string>({
  name: 'Date',
  description: 'ISO 8601 date(yyyy-MM-dd)',
  serialize(value) {
    if (typeof value === 'string') return value;
    throw new TypeError('Date serialize: string が必要');
  },
  parseValue(value) {
    if (typeof value !== 'string') throw new TypeError('Date parseValue: string が必要');
    return value;
  },
  parseLiteral(ast) {
    if (ast.kind !== Kind.STRING) throw new TypeError('Date parseLiteral: string literal が必要');
    return ast.value;
  },
});

export interface BffContext {
  loaders: DataLoaderContext;
  authToken: string | null;
}

export const resolvers = {
  Date: Date_,
  Query: {
    health: () => 'ok',
    salesOrder: async (
      _parent: unknown,
      args: { orderId: string },
      context: BffContext,
    ) => {
      return context.loaders.salesOrderById.load(args.orderId);
    },
  },
};
