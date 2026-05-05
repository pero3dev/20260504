package com.example.inventory.commons.tenant;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 検証済みJWT(Identity Brokerが発行、ADR-0007)から {@code tenant_id} を抽出し、 当該リクエスト全体で {@link TenantContext}
 * に束ねるサーブレットフィルタ。
 *
 * <p>JWT認証フィルタの後・DBアクセスのコードの前に配置すること。
 */
public class TenantContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            resolveTenant().ifPresent(TenantContext::set);
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private static java.util.Optional<TenantId> resolveTenant() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            return java.util.Optional.empty();
        }
        String tid = jwt.getClaimAsString("tenant_id");
        return tid == null ? java.util.Optional.empty() : java.util.Optional.of(new TenantId(tid));
    }
}
