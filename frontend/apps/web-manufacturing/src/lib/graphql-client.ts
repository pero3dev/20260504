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

export interface WorkOrderQueryResult {
  workOrder: {
    id: string;
    code: string;
    productSkuCode: string;
    locationId: string;
    plannedQuantity: number;
    status: 'PLANNED' | 'RELEASED' | 'COMPLETED' | 'CANCELLED';
    plannedStartDate: string | null;
    version: number;
  } | null;
}

export async function fetchWorkOrder(workOrderId: string): Promise<WorkOrderQueryResult> {
  return client.request<WorkOrderQueryResult>(WORK_ORDER_QUERY, { workOrderId });
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
