package com.example.inventory.commons.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Map;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.AuthenticationEntryPoint;

class RevocationCheckFilterTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 未認証_は_素通し_revocationStore_を呼ばない() throws ServletException, IOException {
        RevocationStore store = Mockito.mock(RevocationStore.class);
        AuthenticationEntryPoint entryPoint = Mockito.mock(AuthenticationEntryPoint.class);
        RevocationCheckFilter filter = new RevocationCheckFilter(store, entryPoint);
        FilterChain chain = Mockito.mock(FilterChain.class);

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        verify(chain, times(1)).doFilter(any(ServletRequest.class), any(ServletResponse.class));
        verify(store, never()).isUserRevoked(anyLong());
        verify(entryPoint, never()).commence(any(), any(), any());
    }

    @Test
    void 認証済_かつ_revoke_済みなら_chain_を呼ばず_entryPoint_を_commence()
            throws ServletException, IOException {
        RevocationStore store = Mockito.mock(RevocationStore.class);
        AuthenticationEntryPoint entryPoint = Mockito.mock(AuthenticationEntryPoint.class);
        RevocationCheckFilter filter = new RevocationCheckFilter(store, entryPoint);
        FilterChain chain = Mockito.mock(FilterChain.class);

        Jwt jwt = jwtWithSubject("424242");
        SecurityContextHolder.getContext()
                .setAuthentication(new TestingAuthenticationToken(jwt, jwt, "ROLE_VIEWER"));
        when(store.isUserRevoked(424242L)).thenReturn(true);

        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, chain);

        verify(chain, never()).doFilter(any(ServletRequest.class), any(ServletResponse.class));
        verify(entryPoint, times(1)).commence(eq(req), eq(res), any());
    }

    @Test
    void 認証済_かつ_revoke_無し_は_素通し() throws ServletException, IOException {
        RevocationStore store = Mockito.mock(RevocationStore.class);
        AuthenticationEntryPoint entryPoint = Mockito.mock(AuthenticationEntryPoint.class);
        RevocationCheckFilter filter = new RevocationCheckFilter(store, entryPoint);
        FilterChain chain = Mockito.mock(FilterChain.class);

        Jwt jwt = jwtWithSubject("100");
        SecurityContextHolder.getContext()
                .setAuthentication(new TestingAuthenticationToken(jwt, jwt, "ROLE_VIEWER"));
        when(store.isUserRevoked(100L)).thenReturn(false);

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        verify(chain, times(1)).doFilter(any(ServletRequest.class), any(ServletResponse.class));
        verify(entryPoint, never()).commence(any(), any(), any());
    }

    private static Jwt jwtWithSubject(String sub) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(sub)
                .claims(c -> c.putAll(Map.of("sub", sub)))
                .build();
    }
}
