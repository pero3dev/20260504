import { GraphQLClient, gql } from 'graphql-request';

import { getAuthToken } from './auth';

const endpoint = '/graphql';

export const client = new GraphQLClient(endpoint, {
  requestMiddleware: (req) => {
    const token = getAuthToken();
    if (token) {
      return {
        ...req,
        headers: { ...(req.headers as Record<string, string>), authorization: `Bearer ${token}` },
      };
    }
    return req;
  },
});

const STOCK_MOVEMENT_QUERY = gql`
  query StockMovement($movementId: ID!) {
    stockMovement(movementId: $movementId) {
      movementId
      skuId
      locationId
      direction
      quantity
      occurredAt
    }
  }
`;

export interface StockMovementQueryResult {
  stockMovement: {
    movementId: string;
    skuId: string;
    locationId: string;
    direction: string;
    quantity: number;
    occurredAt: string;
  } | null;
}

export async function fetchStockMovement(
  movementId: string,
): Promise<StockMovementQueryResult> {
  return client.request<StockMovementQueryResult>(STOCK_MOVEMENT_QUERY, { movementId });
}
