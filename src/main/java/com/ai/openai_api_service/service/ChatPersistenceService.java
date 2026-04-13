package com.ai.openai_api_service.service;

import com.ai.openai_api_service.entity.ChatMessageEntity;
import com.ai.openai_api_service.entity.ChatSessionEntity;
import com.ai.openai_api_service.model.MessageDto;
import com.ai.openai_api_service.repository.ChatMessageRepository;
import com.ai.openai_api_service.repository.ChatSessionRepository;
import org.springframework.data.domain.PageRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class ChatPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(ChatPersistenceService.class);

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;

    @Value("${chat.session.default-token-limit:200000}")
    private Integer defaultTokenLimit;

    public ChatPersistenceService(ChatSessionRepository chatSessionRepository, ChatMessageRepository chatMessageRepository) {
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
    }

    @Transactional
    public void persistChat(
            String tenantId,
            String userId,
            String sessionId,
            String originalText,
            String sanitizedText,
            String openAiResponse,
            Integer requestTokensUsed
    ) {
        try {
            ChatSessionEntity session = chatSessionRepository.findBySessionId(sessionId).orElseGet(() -> {
                ChatSessionEntity entity = new ChatSessionEntity();
                entity.setTenantId(tenantId);
                entity.setUserId(userId);
                entity.setSessionId(sessionId);
                entity.setStatus("ACTIVE");
                entity.setTokenLimit(defaultTokenLimit);
                entity.setTokensUsed(0);
                return entity;
            });

            int consumed = requestTokensUsed != null ? requestTokensUsed : 0;
            int alreadyUsed = session.getTokensUsed() != null ? session.getTokensUsed() : 0;
            session.setTokensUsed(alreadyUsed + consumed);
            chatSessionRepository.save(session);

            ChatMessageEntity message = new ChatMessageEntity();
            message.setSessionId(sessionId);
            message.setTenantId(tenantId);
            message.setUserId(userId);
            message.setOriginalText(originalText);
            message.setSanitizedText(sanitizedText);
            message.setOpenaiResponse(openAiResponse);
            message.setTokensUsed(consumed);
            chatMessageRepository.save(message);
        } catch (Exception e) {
            // Never fail chat response if persistence fails.
            log.error("Failed to persist chat interaction. sessionId={}, reason={}", sessionId, e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public List<MessageDto> loadHistoryForPrompt(String tenantId, String userId, String sessionId, int maxExchanges) {
        if (maxExchanges <= 0) {
            return List.of();
        }
        List<ChatMessageEntity> rows = chatMessageRepository.findByTenantIdAndUserIdAndSessionIdOrderByCreatedAtDesc(
                tenantId,
                userId,
                sessionId,
                PageRequest.of(0, maxExchanges)
        );
        Collections.reverse(rows);

        List<MessageDto> messages = new ArrayList<>();
        for (ChatMessageEntity row : rows) {
            if (row.getSanitizedText() != null && !row.getSanitizedText().isBlank()) {
                messages.add(new MessageDto("user", row.getSanitizedText()));
            }
            if (row.getOpenaiResponse() != null && !row.getOpenaiResponse().isBlank()) {
                messages.add(new MessageDto("assistant", row.getOpenaiResponse()));
            }
        }
        return messages;
    }

    @Transactional(readOnly = true)
    public List<ChatMessageEntity> loadHistoryForDisplay(String tenantId, String userId, String sessionId, int maxExchanges) {
        if (maxExchanges <= 0) {
            return List.of();
        }
        List<ChatMessageEntity> rows = chatMessageRepository.findByTenantIdAndUserIdAndSessionIdOrderByCreatedAtDesc(
                tenantId,
                userId,
                sessionId,
                PageRequest.of(0, maxExchanges)
        );
        Collections.reverse(rows);
        return rows;
    }

    @Transactional(readOnly = true)
    public List<ChatSessionEntity> listSessions(String tenantId, String userId) {
        return chatSessionRepository.findByTenantIdAndUserIdOrderByUpdatedAtDesc(tenantId, userId);
    }

    @Transactional(readOnly = true)
    public long countSessions(String tenantId, String userId) {
        return chatSessionRepository.countByTenantIdAndUserId(tenantId, userId);
    }

    @Transactional(readOnly = true)
    public List<ChatMessageEntity> loadSessionMessages(String tenantId, String userId, String sessionId) {
        return chatMessageRepository.findByTenantIdAndUserIdAndSessionIdOrderByCreatedAtAsc(tenantId, userId, sessionId);
    }
}
