// Identity Broker の TenantResource(OpenAPI 由来)に対応する frontend 側の型。
// schema 同期は GraphQL Code Generator が担う想定だが、 BFF を介さない直接呼びでも使えるよう手書きで残す。
export type TenantStatus = 'ACTIVE' | 'DEACTIVATED';

export interface Tenant {
  tenantId: string;
  displayName: string;
  status: TenantStatus;
  createdAt: string; // ISO 8601
  deactivatedAt?: string | null;
}
