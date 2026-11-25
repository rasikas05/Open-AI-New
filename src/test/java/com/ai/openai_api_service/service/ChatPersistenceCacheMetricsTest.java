package com.ai.openai_api_service.service;

import com.ai.openai_api_service.entity.RequestLog;
import com.ai.openai_api_service.entity.Session;
import com.ai.openai_api_service.entity.Tenant;
import com.ai.openai_api_service.entity.User;
import com.ai.openai_api_service.model.ChatMode;
import com.ai.openai_api_service.model.OpenAIUsage;
import com.ai.openai_api_service.repository.RequestLogRepository;
import com.ai.openai_api_service.repository.SessionRepository;
import com.ai.openai_api_service.repository.TenantRepository;
import com.ai.openai_api_service.repository.UserRepository;
import com.ai.openai_api_service.service.i18n.SupportedResponseLanguages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Ensures persistChat writes merged cache metrics when cacheMetricsPresent is true,
 * and leaves columns null when metrics were never present.
 */
@ExtendWith(MockitoExtension.class)
class ChatPersistenceCacheMetricsTest {

    @Mock
    private SessionRepository sessionRepository;
    @Mock
    private RequestLogRepository requestLogRepository;
    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private SupportedResponseLanguages supportedResponseLanguages;
    @Mock
    private ChatAttachmentService chatAttachmentService;

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
                supportedResponseLanguages,
                chatAttachmentService,
                50
        );

        tenant = new Tenant();
        user = new User();
        session = new Session();
        session.setTitle("existing");

        when(tenantRepository.findByTenantCode("t1")).thenReturn(Optional.of(tenant));
        when(userRepository.findByTenantAndUsername(tenant, "u1")).thenReturn(Optional.of(user));
        when(sessionRepository.findByTenantAndUserAndSessionId(tenant, user, "s1"))
                .thenReturn(Optional.of(session));
        when(sessionRepository.save(any(Session.class))).thenAnswer(inv -> inv.getArgument(0));
        when(supportedResponseLanguages.isResponseLocale("en_US")).thenReturn(true);
        when(requestLogRepository.save(any(RequestLog.class))).thenAnswer(inv -> {
            RequestLog log = inv.getArgument(0);
            log.setId(99L);
            return log;
        });
    }

    @Test
    void persistChat_withCacheMetricsPresent_storesWriteAndReadTokens() {
        OpenAIUsage usage = new OpenAIUsage(3620, 340, 3960, "global.openai.gpt-5.6-terra");
        usage.setCacheMetricsPresent(true);
        usage.setCacheWriteTokens(0);
        usage.setCachedTokens(1491);

        chatPersistenceService.persistChat(
                "t1", "u1", "s1",
                "q", "q", "answer",
                usage,
                "RAG",
                false,
                "RETRIEVAL_READY",
                10,
                null,
                null,
                ChatMode.AUTO,
                "en",
                "en_US",
                null
        );

        ArgumentCaptor<RequestLog> captor = ArgumentCaptor.forClass(RequestLog.class);
        verify(requestLogRepository).save(captor.capture());
        RequestLog saved = captor.getValue();
        assertEquals(3620, saved.getPromptTokens());
        assertTrue(Boolean.TRUE.equals(saved.getCacheMetricsPresent()));
        assertEquals(0, saved.getCacheWriteTokens());
        assertEquals(1491, saved.getCachedTokens());
    }

    @Test
    void persistChat_withoutCacheMetrics_storesNullCacheColumns() {
        OpenAIUsage usage = new OpenAIUsage(500, 50, 550, "global.openai.gpt-5.6-terra");

        chatPersistenceService.persistChat(
                "t1", "u1", "s1",
                "q", "q", "answer",
                usage,
                "GENERAL",
                false,
                null,
                null,
                null,
                null,
                ChatMode.AUTO,
                "en",
                "en_US",
                null
        );

        ArgumentCaptor<RequestLog> captor = ArgumentCaptor.forClass(RequestLog.class);
        verify(requestLogRepository).save(captor.capture());
        RequestLog saved = captor.getValue();
        assertEquals(500, saved.getPromptTokens());
        assertFalse(Boolean.TRUE.equals(saved.getCacheMetricsPresent()));
        assertNull(saved.getCacheWriteTokens());
        assertNull(saved.getCachedTokens());
    }
}
