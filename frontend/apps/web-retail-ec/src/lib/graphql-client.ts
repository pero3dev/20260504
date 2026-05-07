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

const SKU_QUERY = gql`
  query Sku($skuId: ID!) {
    sku(skuId: $skuId) {
      skuId
      displayName
      inventories {
        skuId
        locationId
        available
        reserved
        updatedAt
      }
    }
  }
`;

interface InventoryFragment {
  skuId: string;
  locationId: string;
  available: number;
  reserved: number;
  updatedAt: string;
}

export interface SkuQueryResult {
  sku: {
    skuId: string;
    displayName: string;
    inventories: InventoryFragment[];
  } | null;
}

export async function fetchSku(skuId: string): Promise<SkuQueryResult> {
  return client.request<SkuQueryResult>(SKU_QUERY, { skuId });
}
