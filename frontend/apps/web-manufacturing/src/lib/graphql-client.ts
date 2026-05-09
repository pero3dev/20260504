import { GraphQLClient, gql } from 'graphql-request';

import { getAuthToken } from './auth';
import type {
  ViewerQuery,
  ViewerQueryVariables,
  WorkOrderQuery,
  WorkOrderQueryVariables,
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

const WORK_ORDER_QUERY = gql`
  query WorkOrder($workOrderId: ID!) {
    workOrder(workOrderId: $workOrderId) {
      id
      code
      productSkuCode
      locationId
      plannedQuantity
      status
      plannedStartDate
      version
    }
  }
`;

/** F6 follow-up phase 2 で hand-written interface から codegen 生成型に置換。 */
export type WorkOrderQueryResult = WorkOrderQuery;

export async function fetchWorkOrder(workOrderId: string): Promise<WorkOrderQueryResult> {
  return client.request<WorkOrderQuery, WorkOrderQueryVariables>(WORK_ORDER_QUERY, {
    workOrderId,
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
