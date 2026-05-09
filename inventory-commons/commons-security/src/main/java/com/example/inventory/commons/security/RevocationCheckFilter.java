package com.example.inventory.commons.security;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 即時 token revocation チェック(ADR-0023)。 {@link
 * org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter}
 * 直後に挿入され、 認証済 JWT の {@code sub} (=userId) を {@link RevocationStore} に問い合わせて revoke 済なら 401 で弾く。
 *
 * <p>未認証(Bearer 無し / public path)はそのまま通す — security chain が後続で許可/拒否を決定する。 認証済かつ revoke 無しもそのまま通す。
 *
 * <p>Redis 不達は {@link RedisRevocationStore} 側で fail-open するため、 本 filter は素直に store の戻り値だけ見る。
 */
public class RevocationCheckFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(RevocationCheckFilter.class);

    private final RevocationStore revocationStore;
    private final AuthenticationEntryPoint entryPoint;

    public RevocationCheckFilter(
            RevocationStore revocationStore, AuthenticationEntryPoint entryPoint) {
        this.revocationStore = revocationStore;
        this.entryPoint = entryPoint;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Long userId = currentUserId();
        if (userId != null && revocationStore.isUserRevoked(userId)) {
            LOG.info("revoked token 受信 userId={} path={}", userId, request.getRequestURI());
            SecurityContextHolder.clearContext();
            entryPoint.commence(
                    request, response, new DisabledException("token revoked for user " + userId));
            return;
        }
        chain.doFilter(request, response);
    }

    /** JWT principal の {@code sub} を long で返す。 認証無し / 数値で無い場合は null。 */
    private static Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            return null;
        }
        String sub = jwt.getSubject();
        if (sub == null) return null;
        try {
            return Long.parseLong(sub);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
