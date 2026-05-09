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

export interface StockMovementQueryResult {
  stockMovement: {
    id: string;
    code: string;
    partnerCode: string;
    skuCode: string;
    locationId: string;
    movementType: 'INBOUND' | 'OUTBOUND' | 'ADJUSTMENT';
    quantity: number;
    status: 'PLANNED' | 'RECEIVED' | 'DISPATCHED' | 'CANCELLED';
    referenceCode: string | null;
    version: number;
  } | null;
}

export async function fetchStockMovement(
  movementId: string,
): Promise<StockMovementQueryResult> {
  return client.request<StockMovementQueryResult>(STOCK_MOVEMENT_QUERY, { movementId });
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

export interface ViewerQueryResult {
  viewer: {
    userId: string;
    tenantId: string;
    roles: string[];
    locale: string;
    locations: string[];
    partners: string[];
  } | null;
}

export async function fetchViewer(): Promise<ViewerQueryResult> {
  return client.request<ViewerQueryResult>(VIEWER_QUERY);
}
