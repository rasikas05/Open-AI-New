package com.ai.openai_api_service.service;

import com.ai.openai_api_service.entity.ChatMessagePreparation;
import com.ai.openai_api_service.entity.ChatMessagePreparation.Status;
import com.ai.openai_api_service.exception.OpenAIException;
import com.ai.openai_api_service.model.ChatPrepareRequest;
import com.ai.openai_api_service.model.ChatPrepareResponse;
import com.ai.openai_api_service.repository.ChatMessagePreparationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

@Service
public class ChatPrepareService {

    private static final Logger log = LoggerFactory.getLogger(ChatPrepareService.class);

    private final ChatMessagePreparationRepository preparationRepository;
    private final TenantService tenantService;
    private final long prepareTtlHours;

    public ChatPrepareService(
            ChatMessagePreparationRepository preparationRepository,
            TenantService tenantService,
            @Value("${chat.attachment.prepare-ttl-hours:24}") long prepareTtlHours
    ) {
        this.preparationRepository = preparationRepository;
        this.tenantService = tenantService;
        this.prepareTtlHours = prepareTtlHours > 0 ? prepareTtlHours : 24L;
    }

    @Transactional
    public ChatPrepareResponse prepare(ChatPrepareRequest request) {
        if (request == null) {
            throw new OpenAIException("Prepare request is required", 400);
        }
        String tenantCode = requireNonBlank(request.getTenantCode(), "tenantCode");
        String userId = requireNonBlank(request.getUserId(), "userId");
        String sessionId = requireNonBlank(request.getSessionId(), "sessionId");

        tenantService.registerUserAndSession(tenantCode, userId, sessionId, 0);

        String messageId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        ChatMessagePreparation preparation = new ChatMessagePreparation();
        preparation.setMessageId(messageId);
        preparation.setTenantCode(tenantCode);
        preparation.setUserId(userId);
        preparation.setSessionId(sessionId);
        preparation.setStatus(Status.PREPARED);
        preparation.setCreatedAt(now);
        preparation.setExpiresAt(now.plus(prepareTtlHours, ChronoUnit.HOURS));
        preparationRepository.save(preparation);

        log.info(
                "Prepared chat messageId={} tenantCode={} sessionId={} expiresAt={}",
                messageId,
                tenantCode,
                sessionId,
                preparation.getExpiresAt()
        );
        return new ChatPrepareResponse(messageId, sessionId);
    }

    /**
     * Lookup only — no expiry/status gate. Used by GET attachment metadata.
     */
    @Transactional(readOnly = true)
    public Optional<ChatMessagePreparation> findPreparation(String messageId) {
        if (messageId == null || messageId.isBlank()) {
            return Optional.empty();
        }
        return preparationRepository.findByMessageId(messageId.trim());
    }

    /**
     * Validates preparation ownership and lifecycle for chat with images/attachments.
     * Does not consume; call {@link #consumePrepared(String)} after successful persist.
     */
    @Transactional(readOnly = true)
    public ChatMessagePreparation requireUsablePreparation(
            String messageId,
            String tenantCode,
            String userId,
            String sessionId
    ) {
        String mid = requireNonBlank(messageId, "messageId");
        ChatMessagePreparation preparation = preparationRepository.findByMessageId(mid)
                .orElseThrow(() -> new OpenAIException("Unknown or invalid messageId", 400));

        if (!preparation.getTenantCode().equals(tenantCode)
                || !preparation.getUserId().equals(userId)
                || !preparation.getSessionId().equals(sessionId)) {
            throw new OpenAIException("messageId does not match tenant, user, or session", 403);
        }

        if (preparation.getStatus() == Status.CONSUMED) {
            throw new OpenAIException("messageId has already been used", 409);
        }

        if (preparation.getExpiresAt() != null && Instant.now().isAfter(preparation.getExpiresAt())) {
            throw new OpenAIException("messageId preparation has expired", 400);
        }

        if (preparation.getStatus() != Status.PREPARED) {
            throw new OpenAIException("messageId is not in a usable state", 400);
        }
        return preparation;
    }

    /**
     * Atomically marks preparation consumed. Returns false if another request won the race.
     */
    @Transactional
    public boolean consumePrepared(String messageId) {
        int updated = preparationRepository.consumeIfPrepared(
                messageId,
                Status.PREPARED,
                Status.CONSUMED,
                Instant.now()
        );
        if (updated == 0) {
            log.warn("Failed to consume messageId={} (already consumed or missing)", messageId);
            return false;
        }
        return true;
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new OpenAIException(field + " is required", 400);
        }
        return value.trim();
    }
}
