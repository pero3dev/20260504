import { GraphQLClient, gql } from 'graphql-request';

import { getAuthToken } from './auth';
import type {
  SalesOrderQuery,
  SalesOrderQueryVariables,
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

const SALES_ORDER_QUERY = gql`
  query SalesOrder($orderId: ID!) {
    salesOrder(orderId: $orderId) {
      id
      code
      partnerCode
      status
      currency
      totalAmount
      requestedDeliveryDate
      items {
        skuCode
        locationId
        quantity
        unitPrice
      }
      version
    }
  }
`;

/**
 * F6 follow-up phase 2 で hand-written interface から codegen 生成型に置換。
 * 旧 `SalesOrderLineQueryResult` interface も生成型 `SalesOrderQuery['salesOrder']['items'][number]`
 * から派生で取れるが、 既存呼出側の参照保持のため alias を残す。
 */
export type SalesOrderQueryResult = SalesOrderQuery;

export async function fetchSalesOrder(
  orderId: string,
): Promise<SalesOrderQueryResult> {
  return client.request<SalesOrderQuery, SalesOrderQueryVariables>(SALES_ORDER_QUERY, {
    orderId,
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
