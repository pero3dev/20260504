import { GraphQLClient, gql } from 'graphql-request';

import { getAuthToken } from './auth';
import type {
  StockMovementQuery,
  StockMovementQueryVariables,
  ViewerQuery,
  ViewerQueryVariables,
} from '../__generated__/graphql';

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
      id
      code
      partnerCode
      skuCode
      locationId
      movementType
      quantity
      status
      referenceCode
      version
    }
  }
`;

/** F6 follow-up phase 2 で hand-written interface から codegen 生成型に置換。 */
export type StockMovementQueryResult = StockMovementQuery;

export async function fetchStockMovement(
  movementId: string,
): Promise<StockMovementQueryResult> {
  return client.request<StockMovementQuery, StockMovementQueryVariables>(STOCK_MOVEMENT_QUERY, {
    movementId,
  });
}

const VIEWER_QUERY = gql`
  query Viewer {
    viewer {
      userId
      tenantId
      roles
      locale
      locations
      partners
    }
  }
`;

export type ViewerQueryResult = ViewerQuery;

export async function fetchViewer(): Promise<ViewerQueryResult> {
  return client.request<ViewerQuery, ViewerQueryVariables>(VIEWER_QUERY);
}
