package com.ai.openai_api_service.controller;

import com.ai.openai_api_service.model.TenantQuotaResponse;
import com.ai.openai_api_service.model.TokenUsageDto;
import com.ai.openai_api_service.model.TopupResponse;
import com.ai.openai_api_service.service.TenantQuotaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

@WebMvcTest(TenantQuotaController.class)
class TenantQuotaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TenantQuotaService tenantQuotaService;

    @Test
    void assignQuotaShouldReturnCreatedQuota() throws Exception {
        TenantQuotaResponse response = new TenantQuotaResponse(
                "tenant-1",
                10000,
                0,
                0,
                "ACTIVE",
                new TokenUsageDto(0, 10000, 10000)
        );
        when(tenantQuotaService.assignQuota("tenant-1", 10000)).thenReturn(response);

        mockMvc.perform(post("/tenant/quota")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":\"tenant-1\",\"baseLimit\":10000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value("tenant-1"))
                .andExpect(jsonPath("$.baseLimit").value(10000))
                .andExpect(jsonPath("$.usage.remaining").value(10000));
    }

    @Test
    void topupShouldReturnUpdatedUsage() throws Exception {
        TopupResponse response = new TopupResponse(
                "Top-up successful",
                "tenant-1",
                500,
                new TokenUsageDto(100, 1500, 1400)
        );
        when(tenantQuotaService.topup("tenant-1", 500)).thenReturn(response);

        mockMvc.perform(post("/tenant/topup")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":\"tenant-1\",\"tokens\":500}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value("tenant-1"))
                .andExpect(jsonPath("$.tokensAdded").value(500))
                .andExpect(jsonPath("$.usage.total").value(1500))
                .andExpect(jsonPath("$.usage.remaining").value(1400));
    }
}
