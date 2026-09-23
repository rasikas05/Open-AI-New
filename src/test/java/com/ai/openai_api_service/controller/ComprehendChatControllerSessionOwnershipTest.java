package com.ai.openai_api_service.controller;

import com.ai.openai_api_service.entity.Session;
import com.ai.openai_api_service.entity.Tenant;
import com.ai.openai_api_service.entity.User;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ComprehendChatController.class)
class ComprehendChatControllerSessionOwnershipTest {

    private static final String SCOPE_AUTHORITY = "SCOPE_default-m2m-resource-server-5kguh6/read";
    private static final String CLIENT_A = "client-a";
    private static final String TENANT_A = "tenant-a";
    private static final String USER_A = "user-a";
    private static final String USER_B = "user-b";
    private static final String SESSION_A = "session-a";

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
    void getSession_correctUser_ok() throws Exception {
        doNothing().when(tenantClientBindingService).assertClientOwnsTenantId(CLIENT_A, TENANT_A);
        Session session = ownedSession();
        when(chatPersistenceService.requireSessionForTenantUser(TENANT_A, USER_A, SESSION_A))
                .thenReturn(session);

        mockMvc.perform(get("/api/chat/comprehend/sessions/" + SESSION_A)
                        .param("tenantId", TENANT_A)
                        .param("userId", USER_A)
                        .with(jwtWithClient(CLIENT_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(SESSION_A));
    }

    @Test
    void getSession_wrongUser_forbidden() throws Exception {
        doNothing().when(tenantClientBindingService).assertClientOwnsTenantId(CLIENT_A, TENANT_A);
        when(chatPersistenceService.requireSessionForTenantUser(TENANT_A, USER_B, SESSION_A))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Session does not belong to this user"));

        mockMvc.perform(get("/api/chat/comprehend/sessions/" + SESSION_A)
                        .param("tenantId", TENANT_A)
                        .param("userId", USER_B)
                        .with(jwtWithClient(CLIENT_A)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Session does not belong to this user"));
    }

    @Test
    void getSession_unknown_notFound() throws Exception {
        doNothing().when(tenantClientBindingService).assertClientOwnsTenantId(CLIENT_A, TENANT_A);
        when(chatPersistenceService.requireSessionForTenantUser(TENANT_A, USER_A, "missing"))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));

        mockMvc.perform(get("/api/chat/comprehend/sessions/missing")
                        .param("tenantId", TENANT_A)
                        .param("userId", USER_A)
                        .with(jwtWithClient(CLIENT_A)))
                .andExpect(status().isNotFound());
    }

    @Test
    void closeSession_wrongUser_forbidden() throws Exception {
        doNothing().when(tenantClientBindingService).assertClientOwnsTenantId(CLIENT_A, TENANT_A);
        when(chatPersistenceService.requireSessionForTenantUser(TENANT_A, USER_B, SESSION_A))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Session does not belong to this user"));

        mockMvc.perform(post("/api/chat/comprehend/sessions/" + SESSION_A + "/close")
                        .param("tenantId", TENANT_A)
                        .param("userId", USER_B)
                        .with(jwtWithClient(CLIENT_A)))
                .andExpect(status().isForbidden());
    }

    @Test
    void closeSession_correctUser_closesAuthorizedSession() throws Exception {
        doNothing().when(tenantClientBindingService).assertClientOwnsTenantId(CLIENT_A, TENANT_A);
        Session authorized = ownedSession();
        Session closed = ownedSession();
        closed.setStatus("CLOSED");
        when(chatPersistenceService.requireSessionForTenantUser(TENANT_A, USER_A, SESSION_A))
                .thenReturn(authorized);
        when(chatPersistenceService.closeAuthorizedSession(authorized)).thenReturn(closed);
        when(lexService.buildLexSessionId(any())).thenReturn("lex-1");

        mockMvc.perform(post("/api/chat/comprehend/sessions/" + SESSION_A + "/close")
                        .param("tenantId", TENANT_A)
                        .param("userId", USER_A)
                        .with(jwtWithClient(CLIENT_A)))
                .andExpect(status().isOk());

        verify(chatPersistenceService).closeAuthorizedSession(authorized);
    }

    private static Session ownedSession() {
        Tenant tenant = new Tenant();
        tenant.setTenantCode(TENANT_A);
        User user = new User();
        user.setUsername(USER_A);
        user.setTenant(tenant);
        Session session = new Session();
        session.setSessionId(SESSION_A);
        session.setTenant(tenant);
        session.setUser(user);
        session.setStatus("ACTIVE");
        return session;
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor jwtWithClient(String clientId) {
        return jwt()
                .authorities(new SimpleGrantedAuthority(SCOPE_AUTHORITY))
                .jwt(j -> j.claim("client_id", clientId));
    }
}
