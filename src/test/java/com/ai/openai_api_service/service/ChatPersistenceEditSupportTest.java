package com.ai.openai_api_service.service;

import com.ai.openai_api_service.entity.RequestLog;
import com.ai.openai_api_service.entity.Session;
import com.ai.openai_api_service.entity.Tenant;
import com.ai.openai_api_service.entity.User;
import com.ai.openai_api_service.model.HistoryMessageDto;
import com.ai.openai_api_service.model.MessageDto;
import com.ai.openai_api_service.repository.RequestLogRepository;
import com.ai.openai_api_service.repository.SessionRepository;
import com.ai.openai_api_service.repository.TenantRepository;
import com.ai.openai_api_service.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatPersistenceEditSupportTest {

    @Mock
    private SessionRepository sessionRepository;
    @Mock
    private RequestLogRepository requestLogRepository;
    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private UserRepository userRepository;

    private ChatPersistenceService chatPersistenceService;

    private Tenant tenant;
    private User user;
    private Session session;

    @BeforeEach
    void setUp() {
        chatPersistenceService = new ChatPersistenceService(
                sessionRepository,
                requestLogRepository,
                tenantRepository,
                userRepository,
                50
        );

        tenant = new Tenant();
        tenant.setTenantCode("tenant1");
        user = new User();
        user.setUsername("user1");
        session = new Session();
        ReflectionTestUtils.setField(session, "id", 10L);
        session.setSessionId("session1");
        session.setTenant(tenant);
        session.setUser(user);
    }

    @Test
    void loadHistoryForDisplay_setsRequestLogIdAndModeOnUserAndAssistant_activeOnly() {
        when(tenantRepository.findByTenantCode("tenant1")).thenReturn(Optional.of(tenant));
        when(userRepository.findByTenantAndUsername(tenant, "user1")).thenReturn(Optional.of(user));

        RequestLog row = new RequestLog();
        row.setId(55L);
        row.setOriginalText("how to create customer");
        row.setOpenaiResponse("Use CRS610");
        row.setActionTaken("rag");
        row.setSanitizedFlag(false);
        row.setMode("DOCS");

        when(requestLogRepository.findActiveBySessionOrderByCreatedAtDesc(
                eq(tenant), eq(user), eq("session1"), any(Pageable.class)
        )).thenReturn(List.of(row));

        List<HistoryMessageDto> display = chatPersistenceService.loadHistoryForDisplay(
                "tenant1", "user1", "session1", 10
        );
        assertEquals(2, display.size());
        assertEquals("user", display.get(0).getRole());
        assertEquals(55L, display.get(0).getRequestLogId());
        assertEquals("docs", display.get(0).getMode());
        assertEquals("assistant", display.get(1).getRole());
        assertEquals(55L, display.get(1).getRequestLogId());
        assertEquals("docs", display.get(1).getMode());

        List<MessageDto> promptHistory = chatPersistenceService.loadHistoryForPrompt(
                "tenant1", "user1", "session1", 10
        );
        assertEquals(2, promptHistory.size());
    }

    @Test
    void enforceSessionRequestLimit_blocksAtMax() {
        when(tenantRepository.findByTenantCode("tenant1")).thenReturn(Optional.of(tenant));
        when(userRepository.findByTenantAndUsername(tenant, "user1")).thenReturn(Optional.of(user));
        when(requestLogRepository.countBySession_TenantAndSession_UserAndSession_SessionId(
                tenant, user, "session1"
        )).thenReturn(50L);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> chatPersistenceService.enforceSessionRequestLimit("tenant1", "user1", "session1")
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void enforceSessionRequestLimit_allowsBelowMax() {
        when(tenantRepository.findByTenantCode("tenant1")).thenReturn(Optional.of(tenant));
        when(userRepository.findByTenantAndUsername(tenant, "user1")).thenReturn(Optional.of(user));
        when(requestLogRepository.countBySession_TenantAndSession_UserAndSession_SessionId(
                tenant, user, "session1"
        )).thenReturn(49L);

        chatPersistenceService.enforceSessionRequestLimit("tenant1", "user1", "session1");
    }

    @Test
    void validateLatestActiveEdit_rejectsNonLatest() {
        RequestLog target = new RequestLog();
        target.setId(100L);
        target.setSession(session);
        when(requestLogRepository.findById(100L)).thenReturn(Optional.of(target));
        when(requestLogRepository.findActiveIdsBySessionOrderByIdDesc(
                eq(tenant), eq(user), eq("session1"), any(Pageable.class)
        )).thenReturn(List.of(200L));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> chatPersistenceService.validateLatestActiveEdit(
                        "tenant1", "user1", "session1", 100L
                )
        );
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void validateLatestActiveEdit_acceptsLatestActive() {
        RequestLog target = new RequestLog();
        target.setId(200L);
        target.setSession(session);
        when(requestLogRepository.findById(200L)).thenReturn(Optional.of(target));
        when(requestLogRepository.findActiveIdsBySessionOrderByIdDesc(
                eq(tenant), eq(user), eq("session1"), any(Pageable.class)
        )).thenReturn(List.of(200L));

        Long sessionPk = chatPersistenceService.validateLatestActiveEdit(
                "tenant1", "user1", "session1", 200L
        );
        assertEquals(10L, sessionPk);
    }

    @Test
    void supersedeEditedRequest_success() {
        when(requestLogRepository.supersedeIfActive(123L, 456L, 10L)).thenReturn(1);
        assertTrue(chatPersistenceService.supersedeEditedRequest(123L, 456L, 10L));
    }

    @Test
    void supersedeEditedRequest_hidesLoserBeforeReturningFalse() {
        when(requestLogRepository.supersedeIfActive(123L, 457L, 10L)).thenReturn(0);

        RequestLog oldRow = new RequestLog();
        oldRow.setId(123L);
        oldRow.setSupersededByRequestLogId(456L);
        when(requestLogRepository.findById(123L)).thenReturn(Optional.of(oldRow));

        RequestLog loser = new RequestLog();
        loser.setId(457L);
        when(requestLogRepository.findById(457L)).thenReturn(Optional.of(loser));
        when(requestLogRepository.saveAndFlush(loser)).thenReturn(loser);

        assertFalse(chatPersistenceService.supersedeEditedRequest(123L, 457L, 10L));

        ArgumentCaptor<RequestLog> captor = ArgumentCaptor.forClass(RequestLog.class);
        verify(requestLogRepository).saveAndFlush(captor.capture());
        assertEquals(456L, captor.getValue().getSupersededByRequestLogId());
        assertEquals(457L, captor.getValue().getId());
    }
}
