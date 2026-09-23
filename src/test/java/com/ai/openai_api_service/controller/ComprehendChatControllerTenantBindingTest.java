package com.ai.openai_api_service.controller;

import com.ai.openai_api_service.model.ResponseFeedbackResponse;
import com.ai.openai_api_service.service.ChatPersistenceService;
import com.ai.openai_api_service.service.ComprehendChatService;
import com.ai.openai_api_service.service.LexService;
import com.ai.openai_api_service.service.ResponseFeedbackService;
import com.ai.openai_api_service.service.TenantClientBindingService;
import com.ai.openai_api_service.service.TenantService;
import com.ai.openai_api_service.service.guided.InMemoryGuidedSearchSessionService;
import com.ai.openai_api_service.service.lex.InMemoryPendingLexSessionService;
import com.ai.openai_api_service.service.query.SearchContextService;
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
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ComprehendChatController.class)
class ComprehendChatControllerTenantBindingTest {

    private static final String SCOPE_AUTHORITY = "SCOPE_default-m2m-resource-server-5kguh6/read";
    private static final String CLIENT_A = "client-a";
    private static final String TENANT_A = "tenant-a";
    private static final String TENANT_B = "tenant-b";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ComprehendChatService comprehendChatService;
    @MockBean
    private ChatPersistenceService chatPersistenceService;
    @MockBean
    private TenantService tenantService;
    @MockBean
    private TenantClientBindingService tenantClientBindingService;
    @MockBean
    private SearchContextService searchContextService;
    @MockBean
    private InMemoryGuidedSearchSessionService guidedSearchSessionService;
    @MockBean
    private InMemoryPendingLexSessionService pendingLexSessionService;
    @MockBean
    private LexService lexService;
    @MockBean
    private ResponseFeedbackService responseFeedbackService;

    @Test
    void feedback_wrongTenant_forbidden() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, TenantClientBindingService.FORBIDDEN_MESSAGE))
                .when(tenantClientBindingService).assertClientOwnsTenantCode(CLIENT_A, TENANT_B);

        mockMvc.perform(post("/api/chat/comprehend/feedback")
                        .with(jwtWithClient(CLIENT_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(feedbackBody(TENANT_B)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getSession_wrongClient_forbidden() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, TenantClientBindingService.FORBIDDEN_MESSAGE))
                .when(tenantClientBindingService).assertClientOwnsTenantId(CLIENT_A, TENANT_B);

        mockMvc.perform(get("/api/chat/comprehend/sessions/s1")
                        .param("tenantId", TENANT_B)
                        .param("userId", "u1")
                        .with(jwtWithClient(CLIENT_A)))
                .andExpect(status().isForbidden());

        verify(tenantClientBindingService).assertClientOwnsTenantId(CLIENT_A, TENANT_B);
    }

    @Test
    void feedback_matchingTenant_allowed() throws Exception {
        doNothing().when(tenantClientBindingService).assertClientOwnsTenantCode(CLIENT_A, TENANT_A);
        when(responseFeedbackService.upsert(any())).thenReturn(new ResponseFeedbackResponse());

        mockMvc.perform(post("/api/chat/comprehend/feedback")
                        .with(jwtWithClient(CLIENT_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(feedbackBody(TENANT_A)))
                .andExpect(status().isOk());
    }

    private static String feedbackBody(String tenantCode) {
        return "{\"tenantCode\":\"" + tenantCode + "\",\"userId\":\"u1\",\"sessionId\":\"s1\","
                + "\"requestLogId\":1,\"feedback\":\"good\"}";
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor jwtWithClient(String clientId) {
        return jwt()
                .authorities(new SimpleGrantedAuthority(SCOPE_AUTHORITY))
                .jwt(j -> j.claim("client_id", clientId));
    }
}
