import type { BffUserClaims } from '@inventory/shared';
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
  user: BffUserClaims | null;
}

export const resolvers = {
  Date: Date_,
  Query: {
    health: () => 'ok',
    workOrder: async (
      _parent: unknown,
      args: { workOrderId: string },
      context: BffContext,
    ) => {
      return context.loaders.workOrderById.load(args.workOrderId);
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
