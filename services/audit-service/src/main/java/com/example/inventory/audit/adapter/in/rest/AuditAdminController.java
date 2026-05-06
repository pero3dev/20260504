package com.example.inventory.audit.adapter.in.rest;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.inventory.audit.application.port.in.ComputeDailyMerkleAnchorUseCase;
import com.example.inventory.audit.application.port.in.VerifyAuditChainUseCase;
import com.example.inventory.audit.application.port.in.VerifyMerkleAnchorUseCase;
import com.example.inventory.commons.tenant.TenantId;

/**
 * 運用 / 監査担当向けの管理 REST。
 *
 * <ul>
 *   <li>{@code GET /admin/audit-chain/verify?tenant=&lt;id&gt;} — チェーン整合性検証(prev_hash 連鎖 + 自己 hash
 *       再計算)
 *   <li>{@code POST /admin/audit-chain/anchor?tenant=&lt;id&gt;&date=YYYY-MM-DD} — Merkle anchor
 *       手動計算(D3、ADR-0008)
 *   <li>{@code GET /admin/audit-chain/anchor/verify?tenant=&lt;id&gt;&date=YYYY-MM-DD} — anchor
 *       整合性検証
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

    private final VerifyAuditChainUseCase chainVerifier;
    private final ComputeDailyMerkleAnchorUseCase anchorCompute;
    private final VerifyMerkleAnchorUseCase anchorVerifier;

    public AuditAdminController(
            VerifyAuditChainUseCase chainVerifier,
            ComputeDailyMerkleAnchorUseCase anchorCompute,
            VerifyMerkleAnchorUseCase anchorVerifier) {
        this.chainVerifier = chainVerifier;
        this.anchorCompute = anchorCompute;
        this.anchorVerifier = anchorVerifier;
    }

    @GetMapping("/verify")
    public ResponseEntity<VerifyAuditChainUseCase.Report> verifyChain(
            @RequestParam("tenant") String tenant) {
        VerifyAuditChainUseCase.Report report = chainVerifier.verify(new TenantId(tenant));
        return ResponseEntity.ok(report);
    }

    @PostMapping("/anchor")
    public ResponseEntity<ComputeDailyMerkleAnchorUseCase.Result> computeAnchor(
            @RequestParam("tenant") String tenant,
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        ComputeDailyMerkleAnchorUseCase.Result result =
                anchorCompute.compute(
                        new ComputeDailyMerkleAnchorUseCase.Command(new TenantId(tenant), date));
        return ResponseEntity.ok(result);
    }

    @GetMapping("/anchor/verify")
    public ResponseEntity<VerifyMerkleAnchorUseCase.Report> verifyAnchor(
            @RequestParam("tenant") String tenant,
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        VerifyMerkleAnchorUseCase.Report report =
                anchorVerifier.verify(
                        new VerifyMerkleAnchorUseCase.Command(new TenantId(tenant), date));
        return ResponseEntity.ok(report);
    }
}
