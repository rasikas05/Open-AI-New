package com.ai.openai_api_service.service;

import com.ai.openai_api_service.entity.ChatMessagePreparation;
import com.ai.openai_api_service.entity.ChatMessagePreparation.Status;
import com.ai.openai_api_service.exception.OpenAIException;
import com.ai.openai_api_service.model.ChatPrepareRequest;
import com.ai.openai_api_service.model.ChatPrepareResponse;
import com.ai.openai_api_service.repository.ChatMessagePreparationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatPrepareServiceTest {

    @Mock
    private ChatMessagePreparationRepository preparationRepository;
    @Mock
    private TenantService tenantService;

    private ChatPrepareService service;

    @BeforeEach
    void setUp() {
        service = new ChatPrepareService(preparationRepository, tenantService, 24);
    }

    @Test
    void prepare_registersSessionAndPersistsUuid() {
        when(preparationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ChatPrepareRequest request = new ChatPrepareRequest();
        request.setTenantCode("TENANT1");
        request.setUserId("USER1");
        request.setSessionId("session-1");

        ChatPrepareResponse response = service.prepare(request);

        assertNotNull(response.getMessageId());
        assertEquals("session-1", response.getSessionId());
        verify(tenantService).registerUserAndSession("TENANT1", "USER1", "session-1", 0);

        ArgumentCaptor<ChatMessagePreparation> captor = ArgumentCaptor.forClass(ChatMessagePreparation.class);
        verify(preparationRepository).save(captor.capture());
        assertEquals(Status.PREPARED, captor.getValue().getStatus());
        assertEquals("TENANT1", captor.getValue().getTenantCode());
        assertTrue(captor.getValue().getExpiresAt().isAfter(Instant.now()));
    }

    @Test
    void requireUsablePreparation_rejectsExpired() {
        ChatMessagePreparation prep = basePrep();
        prep.setExpiresAt(Instant.now().minusSeconds(60));
        when(preparationRepository.findByMessageId("mid-1")).thenReturn(Optional.of(prep));

        OpenAIException ex = assertThrows(
                OpenAIException.class,
                () -> service.requireUsablePreparation("mid-1", "TENANT1", "USER1", "session-1")
        );
        assertEquals(400, ex.getStatusCode());
    }

    @Test
    void requireUsablePreparation_rejectsConsumed() {
        ChatMessagePreparation prep = basePrep();
        prep.setStatus(Status.CONSUMED);
        when(preparationRepository.findByMessageId("mid-1")).thenReturn(Optional.of(prep));

        OpenAIException ex = assertThrows(
                OpenAIException.class,
                () -> service.requireUsablePreparation("mid-1", "TENANT1", "USER1", "session-1")
        );
        assertEquals(409, ex.getStatusCode());
    }

    @Test
    void requireUsablePreparation_rejectsOwnershipMismatch() {
        ChatMessagePreparation prep = basePrep();
        when(preparationRepository.findByMessageId("mid-1")).thenReturn(Optional.of(prep));

        OpenAIException ex = assertThrows(
                OpenAIException.class,
                () -> service.requireUsablePreparation("mid-1", "TENANT1", "OTHER", "session-1")
        );
        assertEquals(403, ex.getStatusCode());
    }

    @Test
    void consumePrepared_usesConditionalUpdate() {
        when(preparationRepository.consumeIfPrepared(
                eq("mid-1"), eq(Status.PREPARED), eq(Status.CONSUMED), any()
        )).thenReturn(1);

        assertTrue(service.consumePrepared("mid-1"));
    }

    private static ChatMessagePreparation basePrep() {
        ChatMessagePreparation prep = new ChatMessagePreparation();
        prep.setMessageId("mid-1");
        prep.setTenantCode("TENANT1");
        prep.setUserId("USER1");
        prep.setSessionId("session-1");
        prep.setStatus(Status.PREPARED);
        prep.setCreatedAt(Instant.now());
        prep.setExpiresAt(Instant.now().plusSeconds(3600));
        return prep;
    }
}
