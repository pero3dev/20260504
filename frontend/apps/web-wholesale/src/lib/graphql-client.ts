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

const SALES_ORDER_QUERY = gql`
  query SalesOrder($salesOrderId: ID!) {
    salesOrder(salesOrderId: $salesOrderId) {
      salesOrderId
      partnerId
      status
      totalAmountJpy
      placedAt
      shippedAt
    }
  }
`;

export interface SalesOrderQueryResult {
  salesOrder: {
    salesOrderId: string;
    partnerId: string;
    status: string;
    totalAmountJpy: number;
    placedAt: string;
    shippedAt: string | null;
  } | null;
}

export async function fetchSalesOrder(
  salesOrderId: string,
): Promise<SalesOrderQueryResult> {
  return client.request<SalesOrderQueryResult>(SALES_ORDER_QUERY, { salesOrderId });
}
