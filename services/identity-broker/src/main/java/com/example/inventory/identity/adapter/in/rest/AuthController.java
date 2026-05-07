package com.example.inventory.identity.adapter.in.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.example.inventory.identity.adapter.in.rest.api.SessionsApi;
import com.example.inventory.identity.adapter.in.rest.api.model.AccessibleTenant;
import com.example.inventory.identity.adapter.in.rest.api.model.CreateSessionRequest;
import com.example.inventory.identity.adapter.in.rest.api.model.CreateSessionResponse;
import com.example.inventory.identity.adapter.in.rest.api.model.ExchangeFederatedTokenRequest;
import com.example.inventory.identity.adapter.in.rest.api.model.SelectTenantRequest;
import com.example.inventory.identity.adapter.in.rest.api.model.SelectTenantResponse;
import com.example.inventory.identity.application.port.in.AuthenticateUseCase;
import com.example.inventory.identity.application.port.in.ExchangeFederatedTokenUseCase;
import com.example.inventory.identity.application.port.in.SelectTenantUseCase;

/**
 * 認証 REST 入力アダプタ。OpenAPI 仕様(docs/openapi/identity-broker.yaml)から生成された {@link SessionsApi}
 * を実装する(ADR-0006)。
 */
@RestController
public class AuthController implements SessionsApi {

    private final AuthenticateUseCase authenticate;
    private final SelectTenantUseCase selectTenant;
    private final ExchangeFederatedTokenUseCase exchangeFederatedToken;

    public AuthController(
            AuthenticateUseCase authenticate,
            SelectTenantUseCase selectTenant,
            ExchangeFederatedTokenUseCase exchangeFederatedToken) {
        this.authenticate = authenticate;
        this.selectTenant = selectTenant;
        this.exchangeFederatedToken = exchangeFederatedToken;
    }

    @Override
    public ResponseEntity<CreateSessionResponse> createSession(CreateSessionRequest request) {
        AuthenticateUseCase.Result result =
                authenticate.authenticate(
                        new AuthenticateUseCase.Command(request.getEmail(), request.getPassword()));
        return ResponseEntity.ok(toCreateSessionResponse(result));
    }

    @Override
    public ResponseEntity<CreateSessionResponse> exchangeFederatedToken(
            ExchangeFederatedTokenRequest request) {
        ExchangeFederatedTokenUseCase.Result result =
                exchangeFederatedToken.exchange(
                        new ExchangeFederatedTokenUseCase.Command(
                                request.getProviderAccessToken()));

        CreateSessionResponse response = new CreateSessionResponse();
        response.setSessionToken(result.sessionToken());
        response.setExpiresInSeconds((int) result.expiresInSeconds());
        result.accessibleTenants()
                .forEach(
                        m -> {
                            AccessibleTenant t = new AccessibleTenant();
                            t.setTenantId(m.tenantId().value());
                            t.setDisplayName(m.tenantDisplayName());
                            response.addAccessibleTenantsItem(t);
                        });
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<SelectTenantResponse> selectTenant(SelectTenantRequest request) {
        SelectTenantUseCase.Result result =
                selectTenant.selectTenant(
                        new SelectTenantUseCase.Command(
                                request.getSessionToken(), request.getTenantId()));

        SelectTenantResponse response = new SelectTenantResponse();
        response.setAccessToken(result.accessToken());
        response.setTokenType("Bearer");
        response.setExpiresInSeconds((int) result.expiresInSeconds());
        return ResponseEntity.ok(response);
    }

    private static CreateSessionResponse toCreateSessionResponse(
            AuthenticateUseCase.Result result) {
        CreateSessionResponse response = new CreateSessionResponse();
        response.setSessionToken(result.sessionToken());
        response.setExpiresInSeconds((int) result.expiresInSeconds());
        result.accessibleTenants()
                .forEach(
                        m -> {
                            AccessibleTenant t = new AccessibleTenant();
                            t.setTenantId(m.tenantId().value());
                            t.setDisplayName(m.tenantDisplayName());
                            response.addAccessibleTenantsItem(t);
                        });
        return response;
    }
}
