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

export interface SalesOrderLineQueryResult {
  skuCode: string;
  locationId: string;
  quantity: number;
  unitPrice: number;
}

export interface SalesOrderQueryResult {
  salesOrder: {
    id: string;
    code: string;
    partnerCode: string;
    status: 'PLACED' | 'SHIPPED' | 'CANCELLED';
    currency: string;
    totalAmount: number;
    requestedDeliveryDate: string | null;
    items: SalesOrderLineQueryResult[];
    version: number;
  } | null;
}

export async function fetchSalesOrder(
  orderId: string,
): Promise<SalesOrderQueryResult> {
  return client.request<SalesOrderQueryResult>(SALES_ORDER_QUERY, { orderId });
}
