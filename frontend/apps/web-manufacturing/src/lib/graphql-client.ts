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

const WORK_ORDER_QUERY = gql`
  query WorkOrder($workOrderId: ID!) {
    workOrder(workOrderId: $workOrderId) {
      workOrderId
      productSkuId
      status
      startedAt
      completedAt
    }
  }
`;

export interface WorkOrderQueryResult {
  workOrder: {
    workOrderId: string;
    productSkuId: string;
    status: string;
    startedAt: string;
    completedAt: string | null;
  } | null;
}

export async function fetchWorkOrder(workOrderId: string): Promise<WorkOrderQueryResult> {
  return client.request<WorkOrderQueryResult>(WORK_ORDER_QUERY, { workOrderId });
}
