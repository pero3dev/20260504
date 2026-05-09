import { GraphQLClient, gql } from 'graphql-request';

import { getAuthToken } from './auth';
import type {
  InventoryQuery,
  InventoryQueryVariables,
  ViewerQuery,
  ViewerQueryVariables,
} from '../__generated__/graphql';

// dev は Vite proxy で /graphql → http://localhost:4001、 prod は ALB で同 origin。
const endpoint = '/graphql';

export const client = new GraphQLClient(endpoint, {
  // 認可は F2 で oidc-client-ts から取得した token に差し替え。
  requestMiddleware: (req) => {
    const token = getAuthToken();
    if (token) {
      return {
        ...req,
        headers: {
          ...(req.headers as Record<string, string>),
          authorization: `Bearer ${token}`,
        },
      };
    }
    return req;
  },
});

const INVENTORY_QUERY = gql`
  query Inventory($inventoryId: ID!) {
    inventory(inventoryId: $inventoryId) {
      id
      skuId
      locationId
      available
      reserved
      version
    }
  }
`;

/**
 * F6 follow-up phase 1(retail-ec pilot)で `InventoryQueryResult` interface を
 * codegen `InventoryQuery` 型に置き換え。 schema 駆動で drift を typecheck で検知できる。
 */
export type InventoryQueryResult = InventoryQuery;

export async function fetchInventory(inventoryId: string): Promise<InventoryQueryResult> {
  return client.request<InventoryQuery, InventoryQueryVariables>(INVENTORY_QUERY, {
    inventoryId,
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
