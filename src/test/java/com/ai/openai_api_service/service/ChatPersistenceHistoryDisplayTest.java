package com.ai.openai_api_service.service;

import com.ai.openai_api_service.entity.RequestLog;
import com.ai.openai_api_service.entity.Tenant;
import com.ai.openai_api_service.entity.User;
import com.ai.openai_api_service.model.ChatAttachmentDto;
import com.ai.openai_api_service.model.HistoryMessageDto;
import com.ai.openai_api_service.model.MessageDto;
import com.ai.openai_api_service.repository.RequestLogRepository;
import com.ai.openai_api_service.repository.SessionRepository;
import com.ai.openai_api_service.repository.TenantRepository;
import com.ai.openai_api_service.repository.UserRepository;
import com.ai.openai_api_service.service.i18n.SupportedResponseLanguages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
    @Mock
    private ChatAttachmentService chatAttachmentService;

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
                new SupportedResponseLanguages(""),
                chatAttachmentService,
                50
        );
        tenant = new Tenant();
        tenant.setTenantCode("tenant1");
        user = new User();
        user.setUsername("user1");
    }

    @Test
    void loadHistoryForDisplay_setsRequestLogIdAndModeOnUserAndAssistant() {
        when(tenantRepository.findByTenantCode("tenant1")).thenReturn(Optional.of(tenant));
        when(userRepository.findByTenantAndUsername(tenant, "user1")).thenReturn(Optional.of(user));

        RequestLog row = new RequestLog();
        row.setId(55L);
        row.setOriginalText("how to create customer");
        row.setOpenaiResponse("Use CRS610");
        row.setActionTaken("rag");
        row.setSanitizedFlag(false);
        row.setMode("M3");

        when(requestLogRepository.findActiveBySessionOrderByCreatedAtDesc(
                eq(tenant), eq(user), eq("session1"), any(Pageable.class)
        )).thenReturn(List.of(row));
        when(chatAttachmentService.mapByRequestLogIds(eq("tenant1"), eq(List.of(55L))))
                .thenReturn(Map.of());

        List<HistoryMessageDto> display = chatPersistenceService.loadHistoryForDisplay(
                "tenant1", "user1", "session1", 10
        );
        assertEquals(2, display.size());
        assertEquals("user", display.get(0).getRole());
        assertEquals(55L, display.get(0).getRequestLogId());
        assertEquals("m3", display.get(0).getMode());
        assertEquals("assistant", display.get(1).getRole());
        assertEquals(55L, display.get(1).getRequestLogId());
        assertEquals("m3", display.get(1).getMode());

        List<MessageDto> promptHistory = chatPersistenceService.loadHistoryForPrompt(
                "tenant1", "user1", "session1", 10
        );
        assertEquals(2, promptHistory.size());
        assertEquals("user", promptHistory.get(0).getRole());
        assertEquals("assistant", promptHistory.get(1).getRole());
    }

    @Test
    void loadHistoryForDisplay_enrichesUserTurnWithMessageIdAndAttachments_notAssistant() {
        when(tenantRepository.findByTenantCode("tenant1")).thenReturn(Optional.of(tenant));
        when(userRepository.findByTenantAndUsername(tenant, "user1")).thenReturn(Optional.of(user));

        RequestLog row = new RequestLog();
        row.setId(55L);
        row.setMessageId("mid-55");
        row.setOriginalText("what is this image");
        row.setOpenaiResponse("A valve");
        row.setMode("AUTO");

        when(requestLogRepository.findActiveBySessionOrderByCreatedAtDesc(
                eq(tenant), eq(user), eq("session1"), any(Pageable.class)
        )).thenReturn(List.of(row));

        ChatAttachmentDto a0 = new ChatAttachmentDto(1L, "PID-A", "a.png", "image/png", 10L, 0, "UPLOADED");
        ChatAttachmentDto a1 = new ChatAttachmentDto(2L, "PID-B", "b.png", "image/png", 20L, 1, "UPLOADED");
        ChatAttachmentDto a2 = new ChatAttachmentDto(3L, "PID-C", "c.png", "image/png", 30L, 2, "UPLOADED");
        when(chatAttachmentService.mapByRequestLogIds(eq("tenant1"), eq(List.of(55L))))
                .thenReturn(Map.of(55L, List.of(a0, a1, a2)));

        List<HistoryMessageDto> display = chatPersistenceService.loadHistoryForDisplay(
                "tenant1", "user1", "session1", 10
        );

        assertEquals(2, display.size());
        HistoryMessageDto userTurn = display.get(0);
        assertEquals("user", userTurn.getRole());
        assertEquals("mid-55", userTurn.getMessageId());
        assertEquals(3, userTurn.getAttachments().size());
        assertEquals(0, userTurn.getAttachments().get(0).getImageOrder());
        assertEquals(2, userTurn.getAttachments().get(2).getImageOrder());

        HistoryMessageDto assistantTurn = display.get(1);
        assertEquals("assistant", assistantTurn.getRole());
        assertNull(assistantTurn.getMessageId());
        assertNull(assistantTurn.getAttachments());
    }

    @Test
    void loadHistoryForDisplay_multipleMessages_usesExactlyOneBatchAttachmentQuery() {
        when(tenantRepository.findByTenantCode("tenant1")).thenReturn(Optional.of(tenant));
        when(userRepository.findByTenantAndUsername(tenant, "user1")).thenReturn(Optional.of(user));

        RequestLog first = new RequestLog();
        first.setId(10L);
        first.setMessageId("mid-10");
        first.setOriginalText("first");
        first.setOpenaiResponse("reply1");

        RequestLog second = new RequestLog();
        second.setId(20L);
        second.setMessageId("mid-20");
        second.setOriginalText("second");
        second.setOpenaiResponse("reply2");

        // Repository returns newest-first; service reverses to chronological.
        when(requestLogRepository.findActiveBySessionOrderByCreatedAtDesc(
                eq(tenant), eq(user), eq("session1"), any(Pageable.class)
        )).thenReturn(List.of(second, first));

        Map<Long, List<ChatAttachmentDto>> batch = new LinkedHashMap<>();
        batch.put(10L, List.of(
                new ChatAttachmentDto(1L, "P1", "a.png", "image/png", 1L, 0, "UPLOADED")
        ));
        batch.put(20L, List.of(
                new ChatAttachmentDto(2L, "P2", "b.png", "image/png", 1L, 0, "UPLOADED"),
                new ChatAttachmentDto(3L, "P3", "c.png", "image/png", 1L, 1, "UPLOADED")
        ));
        when(chatAttachmentService.mapByRequestLogIds(eq("tenant1"), eq(List.of(10L, 20L))))
                .thenReturn(batch);

        List<HistoryMessageDto> display = chatPersistenceService.loadHistoryForDisplay(
                "tenant1", "user1", "session1", 10
        );

        assertEquals(4, display.size());
        assertEquals("mid-10", display.get(0).getMessageId());
        assertEquals(1, display.get(0).getAttachments().size());
        assertEquals("mid-20", display.get(2).getMessageId());
        assertEquals(2, display.get(2).getAttachments().size());
        assertNull(display.get(1).getAttachments());
        assertNull(display.get(3).getAttachments());

        verify(chatAttachmentService, times(1)).mapByRequestLogIds(eq("tenant1"), eq(List.of(10L, 20L)));
        verify(chatAttachmentService, never()).listByRequestLogIds(any(), any());
    }

    @Test
    void loadHistoryForDisplay_legacyNullMessageId_leavesMessageIdNullAndEmptyAttachments() {
        when(tenantRepository.findByTenantCode("tenant1")).thenReturn(Optional.of(tenant));
        when(userRepository.findByTenantAndUsername(tenant, "user1")).thenReturn(Optional.of(user));

        RequestLog row = new RequestLog();
        row.setId(7L);
        row.setMessageId(null);
        row.setOriginalText("legacy text");
        row.setOpenaiResponse("legacy reply");

        when(requestLogRepository.findActiveBySessionOrderByCreatedAtDesc(
                eq(tenant), eq(user), eq("session1"), any(Pageable.class)
        )).thenReturn(List.of(row));
        when(chatAttachmentService.mapByRequestLogIds(eq("tenant1"), eq(List.of(7L))))
                .thenReturn(Map.of());

        List<HistoryMessageDto> display = chatPersistenceService.loadHistoryForDisplay(
                "tenant1", "user1", "session1", 10
        );

        assertEquals(2, display.size());
        assertNull(display.get(0).getMessageId());
        assertTrue(display.get(0).getAttachments().isEmpty());
        assertNull(display.get(1).getMessageId());
        assertNull(display.get(1).getAttachments());
    }
}
