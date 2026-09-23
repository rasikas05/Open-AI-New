package com.ai.openai_api_service.controller;

import com.ai.openai_api_service.model.UserResponse;
import com.ai.openai_api_service.service.TenantClientBindingService;
import com.ai.openai_api_service.service.TenantService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TenantController.class)
class TenantControllerTenantBindingTest {

    private static final String SCOPE_AUTHORITY = "SCOPE_default-m2m-resource-server-5kguh6/read";
    private static final String CLIENT_A = "client-a";
    private static final String TENANT_A = "tenant-a";
    private static final String TENANT_B = "tenant-b";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TenantService tenantService;

    @MockBean
    private TenantClientBindingService tenantClientBindingService;

    @Test
    void listUsers_matchingTenant_allowed() throws Exception {
        doNothing().when(tenantClientBindingService).assertClientOwnsTenantId(CLIENT_A, TENANT_A);
        when(tenantService.getUsersForTenant(TENANT_A)).thenReturn(List.of(
                new UserResponse(1L, TENANT_A, "u1", LocalDateTime.now())
        ));

        mockMvc.perform(get("/api/tenant/" + TENANT_A + "/users")
                        .with(jwtWithClient(CLIENT_A)))
                .andExpect(status().isOk());
    }

    @Test
    void listUsers_wrongTenant_forbidden() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, TenantClientBindingService.FORBIDDEN_MESSAGE))
                .when(tenantClientBindingService).assertClientOwnsTenantId(CLIENT_A, TENANT_B);

        mockMvc.perform(get("/api/tenant/" + TENANT_B + "/users")
                        .with(jwtWithClient(CLIENT_A)))
                .andExpect(status().isForbidden());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor jwtWithClient(String clientId) {
        return jwt()
                .authorities(new SimpleGrantedAuthority(SCOPE_AUTHORITY))
                .jwt(j -> j.claim("client_id", clientId));
    }
}
