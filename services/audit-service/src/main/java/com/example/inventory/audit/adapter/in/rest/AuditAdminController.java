package com.example.inventory.audit.adapter.in.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.inventory.audit.application.port.in.VerifyAuditChainUseCase;
import com.example.inventory.commons.tenant.TenantId;

/**
 * 運用 / 監査担当向けの管理 REST。MVP の操作:
 *
 * <ul>
 *   <li>{@code GET /admin/audit-chain/verify?tenant=&lt;id&gt;} — テナントの監査チェーン整合性検証
 * </ul>
 *
 * <p>本サービスは内部運用のみで、SecurityConfig は permitAll(NetworkPolicy で内部閉域化)。 本番では:
 *
 * <ul>
 *   <li>NetworkPolicy で administrator/audit-tools の Pod からのみアクセス可
 *   <li>または PlatformSecurity で AUDIT_VIEWER ロール付き JWT 必須に変更
 *   <li>監査ログ自体: 本エンドポイントへのアクセスを別の audit-of-audit に記録
 * </ul>
 */
@RestController
@RequestMapping("/admin/audit-chain")
public class AuditAdminController {

    private final VerifyAuditChainUseCase verifier;

    public AuditAdminController(VerifyAuditChainUseCase verifier) {
        this.verifier = verifier;
    }

    @GetMapping("/verify")
    public ResponseEntity<VerifyAuditChainUseCase.Report> verify(
            @RequestParam("tenant") String tenant) {
        VerifyAuditChainUseCase.Report report = verifier.verify(new TenantId(tenant));
        return ResponseEntity.ok(report);
    }
}
