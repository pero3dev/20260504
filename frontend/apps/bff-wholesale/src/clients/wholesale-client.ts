/**
 * `services/wholesale` REST client(F6 phase 2)。 `GET /v1/sales-orders/{orderId}` を呼び、
 * mock を解消する。 dev local default は `http://localhost:8087`。
 */
export interface SalesOrderLineDto {
  skuCode: string;
  locationId: string;
  quantity: number;
  unitPrice: number;
}

export interface SalesOrderDto {
  id: number;
  code: string;
  partnerCode: string;
  status: 'PLACED' | 'SHIPPED' | 'CANCELLED';
  currency: string;
  totalAmount: number;
  requestedDeliveryDate?: string;
  items: SalesOrderLineDto[];
  version: number;
}

export class WholesaleClient {
  private readonly baseUrl: string;

  constructor(baseUrl: string) {
    this.baseUrl = baseUrl.replace(/\/$/, '');
  }

  async getSalesOrder(
    orderId: number,
    authToken: string | null,
  ): Promise<SalesOrderDto | null> {
    const url = `${this.baseUrl}/v1/sales-orders/${orderId}`;
    const headers: Record<string, string> = { accept: 'application/json' };
    if (authToken) headers['authorization'] = `Bearer ${authToken}`;

    const res = await fetch(url, { headers });
    if (res.status === 404) return null;
    if (!res.ok) {
      const body = await res.text().catch(() => '<no body>');
      throw new Error(`wholesale GET salesOrder ${orderId} failed: ${res.status} ${body}`);
    }
    return (await res.json()) as SalesOrderDto;
  }
}
