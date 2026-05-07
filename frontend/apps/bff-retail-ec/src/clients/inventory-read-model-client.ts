/**
 * inventory-read-model(`services/inventory-read-model`)の REST client(F6)。
 *
 * <p>{@code GET /v1/inventories/{inventoryId}} を呼び、 in-memory mock を解消する。
 * dev local は `http://localhost:8080`、 production cluster では K8s Service DNS
 * (`http://inventory-read-model.inventory-read-model.svc.cluster.local:8080`)を環境変数で渡す。
 *
 * <p>認証は呼出元(GraphQL resolver)から JWT を受け取って Authorization header に乗せる。 BFF 自身は
 * 検証せず pass-through(F2 で BFF 側 verify を入れる)。
 */
export interface InventoryDto {
  id: number;
  skuId: string;
  locationId: string;
  available: number;
  reserved: number;
  version: number;
}

export class InventoryReadModelClient {
  private readonly baseUrl: string;

  constructor(baseUrl: string) {
    this.baseUrl = baseUrl.replace(/\/$/, '');
  }

  /**
   * @returns 該当 inventory が無い(backend が 404)場合は null
   * @throws Error fetch 自体が失敗 / 5xx / その他想定外応答
   */
  async getInventory(
    inventoryId: number,
    authToken: string | null,
  ): Promise<InventoryDto | null> {
    const url = `${this.baseUrl}/v1/inventories/${inventoryId}`;
    const headers: Record<string, string> = { accept: 'application/json' };
    if (authToken) headers['authorization'] = `Bearer ${authToken}`;

    const res = await fetch(url, { headers });
    if (res.status === 404) return null;
    if (!res.ok) {
      const body = await res.text().catch(() => '<no body>');
      throw new Error(
        `inventory-read-model GET ${inventoryId} failed: ${res.status} ${body}`,
      );
    }
    return (await res.json()) as InventoryDto;
  }
}
