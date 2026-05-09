import { GraphQLClient, gql } from 'graphql-request';

import { getAuthToken } from './auth';

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

export interface InventoryQueryResult {
  inventory: {
    id: string;
    skuId: string;
    locationId: string;
    available: number;
    reserved: number;
    version: number;
  } | null;
}

export async function fetchInventory(inventoryId: string): Promise<InventoryQueryResult> {
  return client.request<InventoryQueryResult>(INVENTORY_QUERY, { inventoryId });
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
