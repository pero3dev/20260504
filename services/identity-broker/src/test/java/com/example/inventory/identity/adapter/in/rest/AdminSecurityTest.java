package com.example.inventory.identity.adapter.in.rest;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.inventory.commons.security.NoOpRevocationStoreAutoConfiguration;
import com.example.inventory.commons.security.PlatformSecurityAutoConfiguration;
import com.example.inventory.identity.application.port.in.AddUserMembershipUseCase;
import com.example.inventory.identity.application.port.in.DeactivateUserUseCase;
import com.example.inventory.identity.application.port.in.GetUserUseCase;
import com.example.inventory.identity.application.port.in.RegisterUserUseCase;
import com.example.inventory.identity.application.port.in.RemoveUserMembershipUseCase;
import com.example.inventory.identity.application.port.in.RevokeUserUseCase;
import com.example.inventory.identity.config.JwtKeyConfig;
import com.example.inventory.identity.config.SecurityConfig;

/**
 * {@code SecurityConfig} の admin filter chain が {@code /v1/admin/**} に対して JWT 必須 + SUPER_ADMIN role
 * 必須を強制することを HTTP レイヤで検証(A5 follow-up⁴)。
 *
 * <p>{@code with(jwt())} は実 JWT 検証を bypass し、 認証済 principal を直接注入する。 {@link JwtKeyConfig}
 * の自己署名鍵生成は本テストでは不要だが、 {@link SecurityConfig} の {@code JwtDecoder} Bean が JWKSource を要求するため import
 * しておく(filter chain 構築時に Bean 解決が走る)。
 */
@WebMvcTest(controllers = UserAdminController.class)
@Import({
    SecurityConfig.class,
    JwtKeyConfig.class,
    PlatformSecurityAutoConfiguration.class,
    NoOpRevocationStoreAutoConfiguration.class
})
class AdminSecurityTest {

    @Autowired private MockMvc mvc;

    @MockitoBean private GetUserUseCase getUserUseCase;

    @MockitoBean private RegisterUserUseCase registerUserUseCase;

    @MockitoBean private AddUserMembershipUseCase addUserMembershipUseCase;

    @MockitoBean private RemoveUserMembershipUseCase removeUserMembershipUseCase;

    @MockitoBean private DeactivateUserUseCase deactivateUserUseCase;

    @MockitoBean private RevokeUserUseCase revokeUserUseCase;

    @Test
    void admin_endpoint_は_JWT_無しで_401() throws Exception {
        mvc.perform(get("/v1/admin/users")).andExpect(status().isUnauthorized());
    }

    @Test
    void admin_endpoint_は_SUPER_ADMIN_role_無しの_JWT_で_403() throws Exception {
        mvc.perform(
                        get("/v1/admin/users")
                                .with(
                                        jwt().authorities(
                                                        new SimpleGrantedAuthority(
                                                                "ROLE_INVENTORY_MANAGER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void admin_endpoint_は_SUPER_ADMIN_role_有りの_JWT_で_controller_に到達() throws Exception {
        Mockito.when(getUserUseCase.listAll()).thenReturn(List.of());

        mvc.perform(
                        get("/v1/admin/users")
                                .with(
                                        jwt().authorities(
                                                        new SimpleGrantedAuthority(
                                                                "ROLE_SUPER_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("[]"));
    }

    @Test
    void admin_path_配下_の_userId_path_も_JWT_無しで_401() throws Exception {
        mvc.perform(get("/v1/admin/users/42")).andExpect(status().isUnauthorized());
        Mockito.verifyNoInteractions(getUserUseCase);
    }
}
