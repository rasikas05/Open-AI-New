package com.ai.openai_api_service.controller;

import com.ai.openai_api_service.model.ChatResponse;
import com.ai.openai_api_service.service.ChatPersistenceService;
import com.ai.openai_api_service.service.ChatService;
import com.ai.openai_api_service.service.TenantClientBindingService;
import com.ai.openai_api_service.service.TenantService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChatController.class)
class ChatControllerTenantBindingTest {

    private static final String SCOPE_AUTHORITY = "SCOPE_default-m2m-resource-server-5kguh6/read";
    private static final String CLIENT_A = "client-a";
    private static final String TENANT_A = "tenant-a";
    private static final String TENANT_B = "tenant-b";

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
    void chat_clientA_tenantA_allowed() throws Exception {
        doNothing().when(tenantClientBindingService).assertClientOwnsTenantCode(CLIENT_A, TENANT_A);
        when(chatService.chat(any())).thenReturn(new ChatResponse("ok", false));

        mockMvc.perform(post("/api/chat")
                        .with(jwtWithClient(CLIENT_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(chatBody(TENANT_A)))
                .andExpect(status().isOk());

        verify(tenantClientBindingService).assertClientOwnsTenantCode(CLIENT_A, TENANT_A);
    }

    @Test
    void chat_clientA_tenantB_forbidden() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, TenantClientBindingService.FORBIDDEN_MESSAGE))
                .when(tenantClientBindingService).assertClientOwnsTenantCode(CLIENT_A, TENANT_B);

        mockMvc.perform(post("/api/chat")
                        .with(jwtWithClient(CLIENT_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(chatBody(TENANT_B)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value(TenantClientBindingService.FORBIDDEN_MESSAGE));
    }

    @Test
    void history_wrongTenantId_forbidden() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, TenantClientBindingService.FORBIDDEN_MESSAGE))
                .when(tenantClientBindingService).assertClientOwnsTenantId(CLIENT_A, TENANT_B);

        mockMvc.perform(get("/api/chat/history")
                        .param("tenantId", TENANT_B)
                        .param("userId", "u1")
                        .param("sessionId", "s1")
                        .with(jwtWithClient(CLIENT_A)))
                .andExpect(status().isForbidden());
    }

    @Test
    void chat_missingClientId_forbidden() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, TenantClientBindingService.FORBIDDEN_MESSAGE))
                .when(tenantClientBindingService).assertClientOwnsTenantCode(eq(null), eq(TENANT_A));

        mockMvc.perform(post("/api/chat")
                        .with(jwt().authorities(new SimpleGrantedAuthority(SCOPE_AUTHORITY)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(chatBody(TENANT_A)))
                .andExpect(status().isForbidden());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor jwtWithClient(String clientId) {
        return jwt()
                .authorities(new SimpleGrantedAuthority(SCOPE_AUTHORITY))
                .jwt(j -> j.claim("client_id", clientId));
    }

    private static String chatBody(String tenantCode) {
        return "{\"tenantCode\":\"" + tenantCode + "\",\"userId\":\"u1\",\"sessionId\":\"s1\",\"userMessage\":\"hello\"}";
    }
}
