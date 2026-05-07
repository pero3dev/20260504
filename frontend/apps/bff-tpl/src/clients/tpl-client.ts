/**
 * `services/tpl` REST client(F6 phase 2)。 `GET /v1/stock-movements/{movementId}` を呼び、
 * mock を解消する。 dev local default は `http://localhost:8086`。
 */
export interface StockMovementDto {
  id: number;
  code: string;
  partnerCode: string;
  skuCode: string;
  locationId: string;
  movementType: 'INBOUND' | 'OUTBOUND' | 'ADJUSTMENT';
  quantity: number;
  status: 'PLANNED' | 'RECEIVED' | 'DISPATCHED' | 'CANCELLED';
  referenceCode?: string;
  version: number;
}

export class TplClient {
  private readonly baseUrl: string;

  constructor(baseUrl: string) {
    this.baseUrl = baseUrl.replace(/\/$/, '');
  }

  async getStockMovement(
    movementId: number,
    authToken: string | null,
  ): Promise<StockMovementDto | null> {
    const url = `${this.baseUrl}/v1/stock-movements/${movementId}`;
    const headers: Record<string, string> = { accept: 'application/json' };
    if (authToken) headers['authorization'] = `Bearer ${authToken}`;

    const res = await fetch(url, { headers });
    if (res.status === 404) return null;
    if (!res.ok) {
      const body = await res.text().catch(() => '<no body>');
      throw new Error(`tpl GET stockMovement ${movementId} failed: ${res.status} ${body}`);
    }
    return (await res.json()) as StockMovementDto;
  }
}
