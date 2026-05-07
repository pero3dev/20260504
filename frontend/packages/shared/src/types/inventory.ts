// Inventory Read Model(`services/inventory-read-model`)の Redis 投影に対応する型のサブセット。
// BFF が GraphQL 型として再宣言し、 UI は GraphQL Code Generator 出力を使う想定。
export interface InventorySnapshot {
  tenantId: string;
  skuId: string;
  locationId: string;
  available: number;
  reserved: number;
  updatedAt: string; // ISO 8601
}
