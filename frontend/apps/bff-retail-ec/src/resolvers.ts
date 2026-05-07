import type { BffUserClaims } from '@inventory/shared';
import { GraphQLScalarType, Kind } from 'graphql';

import type { DataLoaderContext } from './dataloaders.js';

/** ISO 8601 文字列を出し入れする最小実装の DateTime scalar。 */
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
  /** Identity Broker 由来の生 JWT。 verify 後 backend へ pass-through する。 */
  authToken: string | null;
  /** F2 verify 済み user claim。 verifier 未設定 (= dev) または anonymous リクエストでは null。 */
  user: BffUserClaims | null;
}

export const resolvers = {
  DateTime,
  Query: {
    health: () => 'ok',
    inventory: async (
      _parent: unknown,
      args: { inventoryId: string },
      context: BffContext,
    ) => {
      // GraphQL ID は string で渡るので number に変換。
      return context.loaders.inventoryById.load(args.inventoryId);
    },
  },
};
