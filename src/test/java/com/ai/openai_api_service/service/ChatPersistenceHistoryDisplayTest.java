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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatPersistenceHistoryDisplayTest {

    @Mock
    private SessionRepository sessionRepository;
    @Mock
    private RequestLogRepository requestLogRepository;
    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ChatPersistenceService chatPersistenceService;

    @Test
    void loadHistoryForDisplay_setsRequestLogIdOnAssistantOnly() {
        Tenant tenant = new Tenant();
        tenant.setTenantCode("tenant1");
        User user = new User();
        user.setUsername("user1");
        when(tenantRepository.findByTenantCode("tenant1")).thenReturn(Optional.of(tenant));
        when(userRepository.findByTenantAndUsername(tenant, "user1")).thenReturn(Optional.of(user));

        RequestLog row = new RequestLog();
        row.setId(55L);
        row.setOriginalText("how to create customer");
        row.setOpenaiResponse("Use CRS610");
        row.setActionTaken("rag");
        row.setSanitizedFlag(false);

        when(requestLogRepository.findBySession_TenantAndSession_UserAndSession_SessionIdOrderByCreatedAtDesc(
                eq(tenant), eq(user), eq("session1"), any(Pageable.class)
        )).thenReturn(List.of(row));

        List<HistoryMessageDto> display = chatPersistenceService.loadHistoryForDisplay(
                "tenant1", "user1", "session1", 10
        );
        assertEquals(2, display.size());
        assertEquals("user", display.get(0).getRole());
        assertNull(display.get(0).getRequestLogId());
        assertEquals("assistant", display.get(1).getRole());
        assertEquals(55L, display.get(1).getRequestLogId());

        List<MessageDto> promptHistory = chatPersistenceService.loadHistoryForPrompt(
                "tenant1", "user1", "session1", 10
        );
        assertEquals(2, promptHistory.size());
        // MessageDto has no requestLogId field — prompt path remains unchanged shape
        assertEquals("user", promptHistory.get(0).getRole());
        assertEquals("assistant", promptHistory.get(1).getRole());
    }
}
