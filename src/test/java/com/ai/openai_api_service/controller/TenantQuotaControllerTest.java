package com.ai.openai_api_service.controller;

import com.ai.openai_api_service.model.TenantQuotaResponse;
import com.ai.openai_api_service.model.TokenUsageDto;
import com.ai.openai_api_service.model.TopupResponse;
import com.ai.openai_api_service.service.TenantClientBindingService;
import com.ai.openai_api_service.service.TenantQuotaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TenantQuotaController.class)
class TenantQuotaControllerTest {

    private static final String SCOPE_AUTHORITY = "SCOPE_default-m2m-resource-server-5kguh6/read";
    private static final String CLIENT_A = "client-a";
    private static final String TENANT_A = "tenant-a";
    private static final String TENANT_B = "tenant-b";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TenantQuotaService tenantQuotaService;

    @MockBean
    private TenantClientBindingService tenantClientBindingService;

    @Test
    void assignQuota_sameTenant_ok() throws Exception {
        doNothing().when(tenantClientBindingService).assertClientOwnsTenantCode(CLIENT_A, TENANT_A);
        TenantQuotaResponse response = new TenantQuotaResponse(
                TENANT_A,
                10000,
                0,
                0,
                "ACTIVE",
                new TokenUsageDto(0, 10000, 10000)
        );
        when(tenantQuotaService.assignQuota(TENANT_A, 10000)).thenReturn(response);

        mockMvc.perform(post("/tenant/quota")
                        .with(jwtWithClient(CLIENT_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantCode\":\"" + TENANT_A + "\",\"baseLimit\":10000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantCode").value(TENANT_A))
                .andExpect(jsonPath("$.baseLimit").value(10000))
                .andExpect(jsonPath("$.usage.remaining").value(10000));

        verify(tenantClientBindingService).assertClientOwnsTenantCode(CLIENT_A, TENANT_A);
    }

    @Test
    void topup_sameTenant_ok() throws Exception {
        doNothing().when(tenantClientBindingService).assertClientOwnsTenantCode(CLIENT_A, TENANT_A);
        TopupResponse response = new TopupResponse(
                "Top-up successful",
                TENANT_A,
                500,
                new TokenUsageDto(100, 1500, 1400)
        );
        when(tenantQuotaService.topup(TENANT_A, 500)).thenReturn(response);

        mockMvc.perform(post("/tenant/topup")
                        .with(jwtWithClient(CLIENT_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantCode\":\"" + TENANT_A + "\",\"tokens\":500}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantCode").value(TENANT_A))
                .andExpect(jsonPath("$.tokensAdded").value(500))
                .andExpect(jsonPath("$.usage.total").value(1500))
                .andExpect(jsonPath("$.usage.remaining").value(1400));
    }

    @Test
    void getTokenUsage_crossTenant_forbidden() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, TenantClientBindingService.FORBIDDEN_MESSAGE))
                .when(tenantClientBindingService).assertClientOwnsTenantCode(CLIENT_A, TENANT_B);

        mockMvc.perform(get("/tenant/quota/" + TENANT_B)
                        .with(jwtWithClient(CLIENT_A)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value(TenantClientBindingService.FORBIDDEN_MESSAGE));

        verify(tenantQuotaService, never()).getTokenUsage(anyString());
    }

    @Test
    void assignQuota_crossTenant_forbidden() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, TenantClientBindingService.FORBIDDEN_MESSAGE))
                .when(tenantClientBindingService).assertClientOwnsTenantCode(CLIENT_A, TENANT_B);

        mockMvc.perform(post("/tenant/quota")
                        .with(jwtWithClient(CLIENT_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantCode\":\"" + TENANT_B + "\",\"baseLimit\":10000}"))
                .andExpect(status().isForbidden());

        verify(tenantQuotaService, never()).assignQuota(anyString(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void getTokenUsage_sameTenant_ok() throws Exception {
        doNothing().when(tenantClientBindingService).assertClientOwnsTenantCode(CLIENT_A, TENANT_A);
        when(tenantQuotaService.getTokenUsage(TENANT_A)).thenReturn(new TokenUsageDto(10, 100, 90));

        mockMvc.perform(get("/tenant/quota/" + TENANT_A)
                        .with(jwtWithClient(CLIENT_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remaining").value(90));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor jwtWithClient(String clientId) {
        return jwt()
                .authorities(new SimpleGrantedAuthority(SCOPE_AUTHORITY))
                .jwt(j -> j.claim("client_id", clientId));
    }
}
