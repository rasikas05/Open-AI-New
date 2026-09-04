package com.ai.openai_api_service.service;

import com.ai.openai_api_service.entity.RequestLog;
import com.ai.openai_api_service.entity.Session;
import com.ai.openai_api_service.entity.Tenant;
import com.ai.openai_api_service.entity.User;
import com.ai.openai_api_service.repository.RequestLogRepository;
import com.ai.openai_api_service.repository.SessionRepository;
import com.ai.openai_api_service.repository.TenantRepository;
import com.ai.openai_api_service.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatPersistenceOpenAiResponseIdTest {

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
        user = new User();
        when(tenantRepository.findByTenantCode("T1")).thenReturn(Optional.of(tenant));
        when(userRepository.findByTenantAndUsername(tenant, "U1")).thenReturn(Optional.of(user));
    }

    @Test
    void findLatestOpenAiResponseId_returnsMostRecentActiveId() {
        when(requestLogRepository.findLatestOpenAiResponseIds(eq(tenant), eq(user), eq("sess-1"), any(Pageable.class)))
                .thenReturn(List.of("resp_latest"));

        assertEquals("resp_latest", chatPersistenceService.findLatestOpenAiResponseId("T1", "U1", "sess-1"));
    }

    @Test
    void findLatestOpenAiResponseId_returnsNullWhenMissing() {
        when(requestLogRepository.findLatestOpenAiResponseIds(eq(tenant), eq(user), eq("sess-1"), any(Pageable.class)))
                .thenReturn(List.of());

        assertNull(chatPersistenceService.findLatestOpenAiResponseId("T1", "U1", "sess-1"));
    }

    @Test
    void persistChat_savesOpenAiResponseIdOnRequestLog() {
        Session session = new Session();
        session.setSessionId("sess-1");
        when(sessionRepository.findByTenantAndUserAndSessionId(tenant, user, "sess-1"))
                .thenReturn(Optional.of(session));
        when(sessionRepository.save(session)).thenAnswer(inv -> inv.getArgument(0));
        when(requestLogRepository.save(any(RequestLog.class))).thenAnswer(inv -> {
            RequestLog log = inv.getArgument(0);
            log.setId(99L);
            return log;
        });

        Long id = chatPersistenceService.persistChat(
                "T1",
                "U1",
                "sess-1",
                "hello",
                "hello",
                "reply",
                null,
                "gpt_infor",
                false,
                null,
                null,
                null,
                null,
                null,
                "resp_new_99"
        );

        assertEquals(99L, id);
        verify(requestLogRepository).save(org.mockito.ArgumentMatchers.argThat(log ->
                "resp_new_99".equals(log.getOpenaiResponseId())
        ));
    }
}
