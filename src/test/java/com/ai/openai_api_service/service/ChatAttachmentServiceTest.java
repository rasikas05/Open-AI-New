package com.ai.openai_api_service.service;

import com.ai.openai_api_service.entity.ChatAttachment;
import com.ai.openai_api_service.entity.ChatMessagePreparation;
import com.ai.openai_api_service.entity.ChatMessagePreparation.Status;
import com.ai.openai_api_service.entity.RequestLog;
import com.ai.openai_api_service.entity.Session;
import com.ai.openai_api_service.entity.Tenant;
import com.ai.openai_api_service.entity.User;
import com.ai.openai_api_service.exception.OpenAIException;
import com.ai.openai_api_service.model.ChatAttachmentDto;
import com.ai.openai_api_service.model.ChatAttachmentRef;
import com.ai.openai_api_service.model.ChatRequest;
import com.ai.openai_api_service.model.ImageAttachmentRequest;
import com.ai.openai_api_service.repository.ChatAttachmentRepository;
import com.ai.openai_api_service.repository.RequestLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatAttachmentServiceTest {

    @Mock
    private ChatAttachmentRepository attachmentRepository;
    @Mock
    private ChatPrepareService chatPrepareService;
    @Mock
    private RequestLogRepository requestLogRepository;

    private ChatAttachmentService service;

    @BeforeEach
    void setUp() {
        service = new ChatAttachmentService(attachmentRepository, chatPrepareService, requestLogRepository);
    }

    @Test
    void textOnly_doesNotRequireMessageId() {
        ChatRequest request = new ChatRequest();
        request.setUserMessage("hello");
        assertTrue(!service.requiresMessageId(request));
    }

    @Test
    void validateForChat_requiresMessageIdWhenImagesPresent() {
        ChatRequest request = baseImageRequest();
        request.setMessageId(null);

        OpenAIException ex = assertThrows(OpenAIException.class, () -> service.validateForChat(request));
        assertEquals(400, ex.getStatusCode());
    }

    @Test
    void validateForChat_rejectsFourAttachments() {
        ChatRequest request = baseImageRequest();
        request.setMessageId("mid-1");
        request.setAttachments(List.of(
                ref("p1", "a.png", 0),
                ref("p2", "b.png", 1),
                ref("p3", "c.png", 2),
                ref("p4", "d.png", 0)
        ));
        when(chatPrepareService.requireUsablePreparation(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(prep());

        OpenAIException ex = assertThrows(OpenAIException.class, () -> service.validateForChat(request));
        assertEquals(400, ex.getStatusCode());
        assertTrue(ex.getMessage().toLowerCase().contains("too many"));
    }

    @Test
    void persistAndFinalize_upsertsAndConsumes() {
        ChatRequest request = baseImageRequest();
        request.setMessageId("mid-1");
        request.setAttachments(List.of(ref("PID-A", "a.png", 0)));
        when(chatPrepareService.requireUsablePreparation(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(prep());
        when(attachmentRepository.findByTenantCodeAndIdmPid("TENANT1", "PID-A"))
                .thenReturn(Optional.empty());
        when(attachmentRepository.save(any())).thenAnswer(inv -> {
            ChatAttachment row = inv.getArgument(0);
            row.setId(10L);
            return row;
        });
        when(chatPrepareService.consumePrepared("mid-1")).thenReturn(true);

        List<ChatAttachmentDto> dtos = service.persistAndFinalize(request, 99L);

        assertEquals(1, dtos.size());
        assertEquals("PID-A", dtos.get(0).getIdmPid());
        assertEquals(0, dtos.get(0).getImageOrder());
        verify(chatPrepareService).consumePrepared("mid-1");
    }

    @Test
    void persistAndFinalize_conflictWhenConsumeLosesRace() {
        ChatRequest request = baseImageRequest();
        request.setMessageId("mid-1");
        request.setAttachments(List.of(ref("PID-A", "a.png", 0)));
        when(chatPrepareService.requireUsablePreparation(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(prep());
        when(attachmentRepository.findByTenantCodeAndIdmPid("TENANT1", "PID-A"))
                .thenReturn(Optional.empty());
        when(attachmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(chatPrepareService.consumePrepared("mid-1")).thenReturn(false);

        OpenAIException ex = assertThrows(
                OpenAIException.class,
                () -> service.persistAndFinalize(request, 99L)
        );
        assertEquals(409, ex.getStatusCode());
    }

    @Test
    void listByMessageId_returnsOneTwoAndThreeAttachmentsOrdered() {
        when(attachmentRepository.findByTenantCodeAndMessageIdOrderByImageOrderAsc("TENANT1", "mid-1"))
                .thenReturn(List.of(
                        attachment(1L, "PID-A", 0),
                        attachment(2L, "PID-B", 1),
                        attachment(3L, "PID-C", 2)
                ));

        List<ChatAttachmentDto> one = service.listByMessageId("TENANT1", "USER1", "session-1", "mid-1");
        assertEquals(3, one.size());
        assertEquals(0, one.get(0).getImageOrder());
        assertEquals(1, one.get(1).getImageOrder());
        assertEquals(2, one.get(2).getImageOrder());
        assertEquals("PID-A", one.get(0).getIdmPid());
        assertEquals("PID-C", one.get(2).getIdmPid());
        verify(chatPrepareService, never()).requireUsablePreparation(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void listByMessageId_knownRequestLogWithNoAttachments_returnsEmpty() {
        when(attachmentRepository.findByTenantCodeAndMessageIdOrderByImageOrderAsc("TENANT1", "mid-1"))
                .thenReturn(List.of());
        when(requestLogRepository.findByMessageId("mid-1")).thenReturn(Optional.of(ownedRequestLog()));

        List<ChatAttachmentDto> result = service.listByMessageId("TENANT1", "USER1", "session-1", "mid-1");
        assertTrue(result.isEmpty());
    }

    @Test
    void listByMessageId_validPreparationWithNoAttachments_returnsEmpty() {
        when(attachmentRepository.findByTenantCodeAndMessageIdOrderByImageOrderAsc("TENANT1", "mid-1"))
                .thenReturn(List.of());
        when(requestLogRepository.findByMessageId("mid-1")).thenReturn(Optional.empty());
        when(chatPrepareService.findPreparation("mid-1")).thenReturn(Optional.of(prep()));

        List<ChatAttachmentDto> result = service.listByMessageId("TENANT1", "USER1", "session-1", "mid-1");
        assertTrue(result.isEmpty());
    }

    @Test
    void listByMessageId_expiredPreparationAlone_returnsEmpty() {
        ChatMessagePreparation expired = prep();
        expired.setExpiresAt(Instant.now().minusSeconds(60));
        expired.setStatus(Status.PREPARED);
        when(attachmentRepository.findByTenantCodeAndMessageIdOrderByImageOrderAsc("TENANT1", "mid-1"))
                .thenReturn(List.of());
        when(requestLogRepository.findByMessageId("mid-1")).thenReturn(Optional.empty());
        when(chatPrepareService.findPreparation("mid-1")).thenReturn(Optional.of(expired));

        List<ChatAttachmentDto> result = service.listByMessageId("TENANT1", "USER1", "session-1", "mid-1");
        assertTrue(result.isEmpty());
        verify(chatPrepareService, never()).requireUsablePreparation(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void listByMessageId_expiredPreparationWithPersistedRequestLog_returnsEmpty() {
        ChatMessagePreparation expired = prep();
        expired.setExpiresAt(Instant.now().minusSeconds(3600));
        expired.setStatus(Status.CONSUMED);
        when(attachmentRepository.findByTenantCodeAndMessageIdOrderByImageOrderAsc("TENANT1", "mid-1"))
                .thenReturn(List.of());
        when(requestLogRepository.findByMessageId("mid-1")).thenReturn(Optional.of(ownedRequestLog()));

        List<ChatAttachmentDto> result = service.listByMessageId("TENANT1", "USER1", "session-1", "mid-1");
        assertTrue(result.isEmpty());
        verify(chatPrepareService, never()).findPreparation(anyString());
    }

    @Test
    void listByMessageId_unknownMessageId_returns404() {
        when(attachmentRepository.findByTenantCodeAndMessageIdOrderByImageOrderAsc("TENANT1", "missing"))
                .thenReturn(List.of());
        when(requestLogRepository.findByMessageId("missing")).thenReturn(Optional.empty());
        when(chatPrepareService.findPreparation("missing")).thenReturn(Optional.empty());

        OpenAIException ex = assertThrows(
                OpenAIException.class,
                () -> service.listByMessageId("TENANT1", "USER1", "session-1", "missing")
        );
        assertEquals(404, ex.getStatusCode());
    }

    @Test
    void listByMessageId_wrongUserOnAttachments_returns403() {
        when(attachmentRepository.findByTenantCodeAndMessageIdOrderByImageOrderAsc("TENANT1", "mid-1"))
                .thenReturn(List.of(attachment(1L, "PID-A", 0)));

        OpenAIException ex = assertThrows(
                OpenAIException.class,
                () -> service.listByMessageId("TENANT1", "OTHER", "session-1", "mid-1")
        );
        assertEquals(403, ex.getStatusCode());
    }

    @Test
    void listByMessageId_wrongSessionOnRequestLog_returns403() {
        when(attachmentRepository.findByTenantCodeAndMessageIdOrderByImageOrderAsc("TENANT1", "mid-1"))
                .thenReturn(List.of());
        when(requestLogRepository.findByMessageId("mid-1")).thenReturn(Optional.of(ownedRequestLog()));

        OpenAIException ex = assertThrows(
                OpenAIException.class,
                () -> service.listByMessageId("TENANT1", "USER1", "other-session", "mid-1")
        );
        assertEquals(403, ex.getStatusCode());
    }

    @Test
    void listByMessageId_wrongTenantOnPreparation_returns403() {
        when(attachmentRepository.findByTenantCodeAndMessageIdOrderByImageOrderAsc("OTHER", "mid-1"))
                .thenReturn(List.of());
        when(requestLogRepository.findByMessageId("mid-1")).thenReturn(Optional.empty());
        when(chatPrepareService.findPreparation("mid-1")).thenReturn(Optional.of(prep()));

        OpenAIException ex = assertThrows(
                OpenAIException.class,
                () -> service.listByMessageId("OTHER", "USER1", "session-1", "mid-1")
        );
        assertEquals(403, ex.getStatusCode());
    }

    private static ChatRequest baseImageRequest() {
        ChatRequest request = new ChatRequest();
        request.setTenantCode("TENANT1");
        request.setUserId("USER1");
        request.setSessionId("session-1");
        request.setUserMessage("check");
        request.setImages(List.of(
                new ImageAttachmentRequest("aaa", "image/png", "a.png")
        ));
        return request;
    }

    private static ChatAttachmentRef ref(String pid, String fileName, int order) {
        ChatAttachmentRef ref = new ChatAttachmentRef();
        ref.setIdmPid(pid);
        ref.setFileName(fileName);
        ref.setMimeType("image/png");
        ref.setImageOrder(order);
        ref.setFileSize(100L);
        return ref;
    }

    private static ChatMessagePreparation prep() {
        ChatMessagePreparation prep = new ChatMessagePreparation();
        prep.setMessageId("mid-1");
        prep.setTenantCode("TENANT1");
        prep.setUserId("USER1");
        prep.setSessionId("session-1");
        prep.setStatus(Status.PREPARED);
        prep.setExpiresAt(Instant.now().plusSeconds(3600));
        return prep;
    }

    private static ChatAttachment attachment(Long id, String pid, int order) {
        ChatAttachment row = new ChatAttachment();
        row.setId(id);
        row.setTenantCode("TENANT1");
        row.setUserId("USER1");
        row.setSessionId("session-1");
        row.setMessageId("mid-1");
        row.setIdmPid(pid);
        row.setFileName(pid + ".png");
        row.setMimeType("image/png");
        row.setImageOrder(order);
        row.setUploadStatus(ChatAttachment.STATUS_UPLOADED);
        return row;
    }

    private static RequestLog ownedRequestLog() {
        Tenant tenant = new Tenant();
        tenant.setTenantCode("TENANT1");
        User user = new User();
        user.setUsername("USER1");
        Session session = new Session();
        session.setTenant(tenant);
        session.setUser(user);
        session.setSessionId("session-1");
        RequestLog log = new RequestLog();
        log.setId(99L);
        log.setMessageId("mid-1");
        log.setSession(session);
        return log;
    }
}
