package com.ai.openai_api_service.service;

import com.ai.openai_api_service.entity.ChatMessageEntity;
import com.ai.openai_api_service.entity.ChatSessionEntity;
import com.ai.openai_api_service.repository.ChatMessageRepository;
import com.ai.openai_api_service.repository.ChatSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
}
