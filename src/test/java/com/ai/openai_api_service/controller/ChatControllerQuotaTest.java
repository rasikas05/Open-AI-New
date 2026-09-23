package com.ai.openai_api_service.controller;

import com.ai.openai_api_service.model.ChatResponse;
import com.ai.openai_api_service.model.TokenUsageDto;
import com.ai.openai_api_service.service.ChatPersistenceService;
import com.ai.openai_api_service.service.ChatService;
import com.ai.openai_api_service.service.TenantClientBindingService;
import com.ai.openai_api_service.service.TenantService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

@WebMvcTest(ChatController.class)
class ChatControllerQuotaTest {

    private static final String SCOPE_AUTHORITY = "SCOPE_default-m2m-resource-server-5kguh6/read";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ChatService chatService;

    @MockBean
    private ChatPersistenceService chatPersistenceService;

    @MockBean
    private TenantService tenantService;

    @MockBean
    private TenantClientBindingService tenantClientBindingService;

    @Test
    void chatShouldReturn429WhenLimitExceeded() throws Exception {
        doNothing().when(tenantClientBindingService).assertClientOwnsTenantCode(anyString(), anyString());

        ChatResponse response = new ChatResponse(
                "Token limit reached for this tenant. Please top up to continue.", false);

        response.setLimitExceeded(true);
        response.setUsage(new TokenUsageDto(1000, 1000, 0));
        response.setUpgradeOptions(List.of("Buy 100 tokens", "Buy 500 tokens", "Buy 5000 tokens"));

        when(chatService.chat(any())).thenReturn(response);

        mockMvc.perform(post("/api/chat")
                        .with(jwt()
                                .authorities(new SimpleGrantedAuthority(SCOPE_AUTHORITY))
                                .jwt(j -> j.claim("client_id", "client-a")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantCode\":\"t1\",\"userId\":\"u1\",\"sessionId\":\"s1\",\"userMessage\":\"hello\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.limitExceeded").value(true))
                .andExpect(jsonPath("$.usage.total").value(1000))
                .andExpect(jsonPath("$.upgradeOptions[0]").value("Buy 100 tokens"));
    }
}
