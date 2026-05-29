package com.ai.openai_api_service.service;

import com.ai.openai_api_service.entity.RequestLog;
import com.ai.openai_api_service.entity.Session;
import com.ai.openai_api_service.entity.Tenant;
import com.ai.openai_api_service.entity.User;
import com.ai.openai_api_service.model.MessageDto;
import com.ai.openai_api_service.model.SessionTitleUpdateResponse;
import com.ai.openai_api_service.repository.RequestLogRepository;
import com.ai.openai_api_service.repository.SessionRepository;
import com.ai.openai_api_service.repository.TenantRepository;
import com.ai.openai_api_service.repository.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Service
public class ChatPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(ChatPersistenceService.class);

    private final SessionRepository sessionRepository;
    private final RequestLogRepository requestLogRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;

    public ChatPersistenceService(
            SessionRepository sessionRepository,
            RequestLogRepository requestLogRepository,
            TenantRepository tenantRepository,
            UserRepository userRepository
    ) {
        this.sessionRepository = sessionRepository;
        this.requestLogRepository = requestLogRepository;
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        log.info("ChatPersistenceService bean initialized: {}", this.getClass().getName());
    }

    @Transactional
    public void persistChat(
            String tenantId,
            String userId,
            String sessionId,
            String originalText,
            String sanitizedText,
            String openAiResponse,
            Integer requestTokensUsed,
            String actionTaken,
            Boolean sanitizedFlag
    ) {

        try {

            log.info(
                    "Starting persistChat for tenantId={}, userId={}, sessionId={}",
                    tenantId,
                    userId,
                    sessionId
            );

            Tenant tenant = tenantRepository.findByTenantCode(tenantId)
                    .orElse(null);

            if (tenant == null) {
                log.warn("Tenant not found for tenantId={}", tenantId);
                return;
            }

            User user = userRepository.findByTenantAndUsername(tenant, userId)
                    .orElse(null);

            if (user == null) {
                log.warn("User not found for tenantId={}, userId={}", tenantId, userId);
                return;
            }

            Session session = sessionRepository.findByTenantAndUserAndSessionId(
                            tenant,
                            user,
                            sessionId
                    )
                    .orElse(null);

            if (session == null) {
                log.warn(
                        "Session not found for tenantId={}, userId={}, sessionId={}",
                        tenantId,
                        userId,
                        sessionId
                );
                return;
            }

            log.info(
                    "Session found. sessionId={}, currentTitle='{}'",
                    sessionId,
                    session.getTitle()
            );

            boolean hasNoTitle =
                    session.getTitle() == null ||
                    session.getTitle().isBlank();

            boolean hasText =
                    (sanitizedText != null && !sanitizedText.isBlank()) ||
                    (originalText != null && !originalText.isBlank());

            log.info(
                    "persistChat title generation state: hasNoTitle={}, hasText={}, sessionTitleBefore='{}'",
                    hasNoTitle,
                    hasText,
                    session.getTitle()
            );

            if (hasNoTitle && hasText) {

                String titleSource =
                        sanitizedText != null && !sanitizedText.isBlank()
                                ? sanitizedText
                                : originalText;

                String generatedTitle = generateSessionTitle(titleSource);

                session.setTitle(generatedTitle);

                log.info(
                        "Generated session title='{}' for sessionId={}",
                        generatedTitle,
                        sessionId
                );
            }

            int consumed = requestTokensUsed != null
                    ? requestTokensUsed
                    : 0;

            Integer existingTokens = session.getTokensUsed();

            if (existingTokens == null) {
                existingTokens = 0;
            }

            session.setTokensUsed(existingTokens + consumed);

            session.setUpdatedAt(LocalDateTime.now());

            Session savedSession = sessionRepository.save(session);

            log.info(
                    "Session saved successfully. sessionId={}, title='{}'",
                    savedSession.getSessionId(),
                    savedSession.getTitle()
            );

            RequestLog message = new RequestLog();

            message.setSession(savedSession);
            message.setOriginalText(originalText);
            message.setSanitizedText(sanitizedText);
            message.setActionTaken(actionTaken);
            message.setSanitizedFlag(sanitizedFlag);
            message.setOpenaiResponse(openAiResponse);
            message.setTokensUsed(consumed);

            RequestLog savedMessage = requestLogRepository.save(message);

            log.info(
                    "Message saved successfully. messageId={}, sessionId={}, title='{}'",
                    savedMessage.getId(),
                    sessionId,
                    savedSession.getTitle()
            );

        } catch (Exception e) {

            log.error(
                    "Failed to persist chat interaction. sessionId={}, reason={}",
                    sessionId,
                    e.getMessage(),
                    e
            );
        }
    }

    private String generateSessionTitle(String text) {

        if (text == null || text.isBlank()) {
            return "Chat Session";
        }

        String cleaned = text
                .trim()
                .replaceAll("[\\r\\n]+", " ")
                .replaceAll("\\s+", " ")
                .trim();

        if (cleaned.isBlank()) {
            return "Chat Session";
        }

        String lower = cleaned.toLowerCase();

        String[] words = lower.split("\\s+");

        int start = 0;

        String[] leadingWords = {
                "please",
                "kindly",
                "could",
                "would",
                "should",
                "can",
                "may",
                "tell",
                "show",
                "give",
                "help",
                "i",
                "me",
                "us",
                "about",
                "want",
                "need",
                "know",
                "a",
                "the"
        };

        while (start < words.length && start < 3) {

            String word = words[start]
                    .replaceAll("[^a-z0-9]", "");

            if (word.isBlank()) {
                start++;
                continue;
            }

            boolean skip = false;

            for (String lead : leadingWords) {
                if (word.equals(lead)) {
                    skip = true;
                    break;
                }
            }

            if (skip) {
                start++;
            } else {
                break;
            }
        }

        int end = Math.min(words.length, start + 6);

        if (end <= start) {
            end = Math.min(words.length, 4);
        }

        StringBuilder builder = new StringBuilder();

        for (int i = start; i < end; i++) {

            String word = words[i]
                    .replaceAll("[^a-z0-9]", "");

            if (word.isBlank()) {
                continue;
            }

            if (builder.length() > 0) {
                builder.append(" ");
            }

            builder.append(
                    Character.toUpperCase(word.charAt(0))
            );

            if (word.length() > 1) {
                builder.append(word.substring(1));
            }
        }

        String title = builder.toString().trim();

        if (title.isBlank()) {
            return "Chat Session";
        }

        return title;
    }

    @Transactional(readOnly = true)
    public List<MessageDto> loadHistoryForPrompt(
            String tenantId,
            String userId,
            String sessionId,
            int maxExchanges
    ) {

        if (maxExchanges <= 0) {
            return List.of();
        }

        Tenant tenant = tenantRepository.findByTenantCode(tenantId)
                .orElse(null);

        if (tenant == null) {
            return List.of();
        }

        User user = userRepository.findByTenantAndUsername(tenant, userId)
                .orElse(null);

        if (user == null) {
            return List.of();
        }

        List<RequestLog> rows =
                requestLogRepository
                        .findBySession_TenantAndSession_UserAndSession_SessionIdOrderByCreatedAtDesc(
                                tenant,
                                user,
                                sessionId,
                                PageRequest.of(0, maxExchanges)
                        );

        Collections.reverse(rows);

        List<MessageDto> messages = new ArrayList<>();

        for (RequestLog row : rows) {

            // Use originalText for UI display; sanitizedText is only for internal processing/debugging
            String userMessageContent = row.getOriginalText() != null && !row.getOriginalText().isBlank()
                    ? row.getOriginalText()
                    : row.getSanitizedText();

            if (userMessageContent != null && !userMessageContent.isBlank()) {

                messages.add(
                    new MessageDto(
                            "user",
                            userMessageContent,
                            row.getSanitizedFlag(),
                            null
                    )
            );
            }

            if (row.getOpenaiResponse() != null &&
                    !row.getOpenaiResponse().isBlank()) {

                messages.add(
                        new MessageDto(
                                "assistant",
                                row.getOpenaiResponse(),
                                null,
                                row.getActionTaken()
                        )
                );
            }
        }

        return messages;
    }

    @Transactional(readOnly = true)
    public List<RequestLog> loadSessionMessages(
            String tenantId,
            String userId,
            String sessionId
    ) {

        Tenant tenant = tenantRepository.findByTenantCode(tenantId)
                .orElse(null);

        if (tenant == null) {
            return List.of();
        }

        User user = userRepository.findByTenantAndUsername(tenant, userId)
                .orElse(null);

        if (user == null) {
            return List.of();
        }

        return requestLogRepository
                .findBySession_TenantAndSession_UserAndSession_SessionIdOrderByCreatedAtAsc(
                        tenant,
                        user,
                        sessionId
                );
    }

    @Transactional(readOnly = true)
    public List<Session> listSessions(
            String tenantId,
            String userId
    ) {

        Tenant tenant = tenantRepository.findByTenantCode(tenantId)
                .orElse(null);

        if (tenant == null) {
            return List.of();
        }

        User user = userRepository.findByTenantAndUsername(tenant, userId)
                .orElse(null);

        if (user == null) {
            return List.of();
        }

        return sessionRepository.findByTenantAndUserOrderByUpdatedAtDesc(
                tenant,
                user
        );
    }

    @Transactional(readOnly = true)
    public long countSessions(
            String tenantId,
            String userId
    ) {

        Tenant tenant = tenantRepository.findByTenantCode(tenantId)
                .orElse(null);

        if (tenant == null) {
            return 0;
        }

        User user = userRepository.findByTenantAndUsername(tenant, userId)
                .orElse(null);

        if (user == null) {
            return 0;
        }

        return sessionRepository.countByTenantAndUser(
                tenant,
                user
        );
    }

    @Transactional
    public SessionTitleUpdateResponse updateSessionTitle(
            String tenantId,
            String userId,
            String sessionId,
            String title
    ) {

        Tenant tenant = tenantRepository.findByTenantCode(tenantId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Tenant not found: " + tenantId
                        )
                );

        User user = userRepository.findByTenantAndUsername(tenant, userId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "User not found: " + userId
                        )
                );

        Session session =
                sessionRepository.findByTenantAndUserAndSessionId(
                                tenant,
                                user,
                                sessionId
                        )
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Session not found: " + sessionId
                                )
                        );

        session.setTitle(title);

        session.setUpdatedAt(LocalDateTime.now());

        Session savedSession = sessionRepository.save(session);

        log.info(
                "Session title updated successfully. sessionId={}, title='{}'",
                sessionId,
                title
        );

        return new SessionTitleUpdateResponse(
                savedSession.getSessionId(),
                savedSession.getTitle(),
                "Session title updated successfully"
        );
    }

    @Transactional(readOnly = true)
    public Session getSessionById(String sessionId) {

        return sessionRepository.findBySessionId(sessionId)
                .orElse(null);
    }

    @Transactional
    public Session closeSessionById(String sessionId) {

        return sessionRepository.findBySessionId(sessionId)
                .map(session -> {

                    session.setStatus("CLOSED");
                    session.setEndTime(LocalDateTime.now());
                    session.setUpdatedAt(LocalDateTime.now());

                    return sessionRepository.save(session);

                })
                .orElse(null);
    }
}