/**
 * `services/manufacturing` REST client(F6 phase 2)。 `GET /v1/work-orders/{workOrderId}` を呼び、
 * mock を解消する。 dev local default は `http://localhost:8088`。
 */
export interface WorkOrderDto {
  id: number;
  code: string;
  productSkuCode: string;
  locationId: string;
  plannedQuantity: number;
  status: 'PLANNED' | 'RELEASED' | 'COMPLETED' | 'CANCELLED';
  plannedStartDate?: string;
  version: number;
}

export class ManufacturingClient {
  private readonly baseUrl: string;

  constructor(baseUrl: string) {
    this.baseUrl = baseUrl.replace(/\/$/, '');
  }

  async getWorkOrder(
    workOrderId: number,
    authToken: string | null,
  ): Promise<WorkOrderDto | null> {
    const url = `${this.baseUrl}/v1/work-orders/${workOrderId}`;
    const headers: Record<string, string> = { accept: 'application/json' };
    if (authToken) headers['authorization'] = `Bearer ${authToken}`;

    const res = await fetch(url, { headers });
    if (res.status === 404) return null;
    if (!res.ok) {
      const body = await res.text().catch(() => '<no body>');
      throw new Error(`manufacturing GET workOrder ${workOrderId} failed: ${res.status} ${body}`);
    }
    return (await res.json()) as WorkOrderDto;
  }
}
