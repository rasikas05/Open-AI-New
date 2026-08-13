package com.ai.openai_api_service.service;

import com.ai.openai_api_service.entity.RequestLog;
import com.ai.openai_api_service.entity.Session;
import com.ai.openai_api_service.entity.Tenant;
import com.ai.openai_api_service.entity.User;
import com.ai.openai_api_service.model.ChatMode;
import com.ai.openai_api_service.model.HistoryMessageDto;
import com.ai.openai_api_service.model.LiveHistoryAuditMetadata;
import com.ai.openai_api_service.model.MessageDto;
import com.ai.openai_api_service.model.OpenAIUsage;
import com.ai.openai_api_service.model.SessionTitleUpdateResponse;
import com.ai.openai_api_service.repository.RequestLogRepository;
import com.ai.openai_api_service.repository.SessionRepository;
import com.ai.openai_api_service.repository.TenantRepository;
import com.ai.openai_api_service.repository.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
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
    private final int maxRequestsPerSession;

    public ChatPersistenceService(
            SessionRepository sessionRepository,
            RequestLogRepository requestLogRepository,
            TenantRepository tenantRepository,
            UserRepository userRepository,
            @Value("${chat.session.max-requests:50}") int maxRequestsPerSession
    ) {
        this.sessionRepository = sessionRepository;
        this.requestLogRepository = requestLogRepository;
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.maxRequestsPerSession = maxRequestsPerSession;
        log.info("ChatPersistenceService bean initialized: {}", this.getClass().getName());
    }

    @Transactional
    public Long persistChat(
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
        OpenAIUsage usage = new OpenAIUsage();
        usage.setTotalTokens(requestTokensUsed != null ? requestTokensUsed : 0);
        return persistChat(
                tenantId,
                userId,
                sessionId,
                originalText,
                sanitizedText,
                openAiResponse,
                usage,
                actionTaken,
                sanitizedFlag,
                null,
                null,
                null,
                null
        );
    }

    @Transactional
    public Long persistChat(
            String tenantId,
            String userId,
            String sessionId,
            String originalText,
            String sanitizedText,
            String openAiResponse,
            OpenAIUsage openAiUsage,
            String actionTaken,
            Boolean sanitizedFlag,
            String retrievalReason,
            Integer retrievalTimeMs
    ) {
        return persistChat(
                tenantId,
                userId,
                sessionId,
                originalText,
                sanitizedText,
                openAiResponse,
                openAiUsage,
                actionTaken,
                sanitizedFlag,
                retrievalReason,
                retrievalTimeMs,
                null,
                null,
                null
        );
    }

    @Transactional
    public Long persistChat(
            String tenantId,
            String userId,
            String sessionId,
            String originalText,
            String sanitizedText,
            String openAiResponse,
            OpenAIUsage openAiUsage,
            String actionTaken,
            Boolean sanitizedFlag,
            String retrievalReason,
            Integer retrievalTimeMs,
            LiveHistoryAuditMetadata auditMetadata
    ) {
        return persistChat(
                tenantId,
                userId,
                sessionId,
                originalText,
                sanitizedText,
                openAiResponse,
                openAiUsage,
                actionTaken,
                sanitizedFlag,
                retrievalReason,
                retrievalTimeMs,
                auditMetadata,
                null,
                null
        );
    }

    @Transactional
    public Long persistChat(
            String tenantId,
            String userId,
            String sessionId,
            String originalText,
            String sanitizedText,
            String openAiResponse,
            OpenAIUsage openAiUsage,
            String actionTaken,
            Boolean sanitizedFlag,
            String retrievalReason,
            Integer retrievalTimeMs,
            LiveHistoryAuditMetadata auditMetadata,
            com.ai.openai_api_service.service.protection.ProtectionAuditSnapshot protectionAudit
    ) {
        return persistChat(
                tenantId,
                userId,
                sessionId,
                originalText,
                sanitizedText,
                openAiResponse,
                openAiUsage,
                actionTaken,
                sanitizedFlag,
                retrievalReason,
                retrievalTimeMs,
                auditMetadata,
                protectionAudit,
                null
        );
    }

    @Transactional
    public Long persistChat(
            String tenantId,
            String userId,
            String sessionId,
            String originalText,
            String sanitizedText,
            String openAiResponse,
            OpenAIUsage openAiUsage,
            String actionTaken,
            Boolean sanitizedFlag,
            String retrievalReason,
            Integer retrievalTimeMs,
            LiveHistoryAuditMetadata auditMetadata,
            com.ai.openai_api_service.service.protection.ProtectionAuditSnapshot protectionAudit,
            ChatMode resolvedMode
    ) {
        try {
            int consumed = resolveConsumedTokens(openAiUsage, null);

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
                return null;
            }

            User user = userRepository.findByTenantAndUsername(tenant, userId)
                    .orElse(null);

            if (user == null) {
                log.warn("User not found for tenantId={}, userId={}", tenantId, userId);
                return null;
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
                return null;
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
            message.setMode(resolvedMode != null ? resolvedMode.name() : ChatMode.AUTO.name());
            if (openAiUsage != null) {
                message.setPromptTokens(openAiUsage.getPromptTokens());
                message.setCompletionTokens(openAiUsage.getCompletionTokens());
                message.setOpenaiModel(openAiUsage.getModel());
            }
            message.setRetrievalReason(retrievalReason);
            message.setRetrievalTimeMs(retrievalTimeMs);
            if (auditMetadata != null) {
                message.setLexIntent(auditMetadata.lexIntent());
                message.setBusinessObject(auditMetadata.businessObject());
                message.setBusinessIdentifier(auditMetadata.businessIdentifier());
            }
            if (protectionAudit != null) {
                message.setBusinessProtectedText(protectionAudit.businessProtectedText());
                message.setPiiSanitizedText(protectionAudit.piiSanitizedText());
                message.setOpenaiResponseRaw(protectionAudit.openaiResponseRaw());
                message.setFinalResponse(protectionAudit.finalResponse());
                message.setBusinessProtectionFlag(protectionAudit.businessProtectionApplied());
                message.setBusinessEntitiesCount(protectionAudit.businessEntitiesCount());
                message.setBusinessEntitiesJson(protectionAudit.businessEntitiesJson());
            }

            RequestLog savedMessage = requestLogRepository.save(message);

            log.info(
                    "Message saved successfully. messageId={}, sessionId={}, title='{}'",
                    savedMessage.getId(),
                    sessionId,
                    savedSession.getTitle()
            );
            return savedMessage.getId();

        } catch (Exception e) {

            log.error(
                    "Failed to persist chat interaction. sessionId={}, reason={}",
                    sessionId,
                    e.getMessage(),
                    e
            );
            return null;
        }
    }

    private int resolveConsumedTokens(OpenAIUsage openAiUsage, Integer legacyTokens) {
        if (openAiUsage != null && openAiUsage.getTotalTokens() != null) {
            return openAiUsage.getTotalTokens();
        }
        return legacyTokens != null ? legacyTokens : 0;
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
                        .findActiveBySessionOrderByCreatedAtDesc(
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

    /**
     * Widget display history with {@code requestLogId} and {@code mode} on user and assistant turns.
     * Active revisions only. Does not replace {@link #loadHistoryForPrompt}.
     */
    @Transactional(readOnly = true)
    public List<HistoryMessageDto> loadHistoryForDisplay(
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
                        .findActiveBySessionOrderByCreatedAtDesc(
                                tenant,
                                user,
                                sessionId,
                                PageRequest.of(0, maxExchanges)
                        );

        Collections.reverse(rows);

        List<HistoryMessageDto> messages = new ArrayList<>();
        for (RequestLog row : rows) {
            String userMessageContent = row.getOriginalText() != null && !row.getOriginalText().isBlank()
                    ? row.getOriginalText()
                    : row.getSanitizedText();
            String modeWire = toModeWireValue(row.getMode());

            if (userMessageContent != null && !userMessageContent.isBlank()) {
                messages.add(new HistoryMessageDto(
                        "user",
                        userMessageContent,
                        row.getSanitizedFlag(),
                        null,
                        row.getId(),
                        modeWire
                ));
            }

            if (row.getOpenaiResponse() != null && !row.getOpenaiResponse().isBlank()) {
                messages.add(new HistoryMessageDto(
                        "assistant",
                        row.getOpenaiResponse(),
                        null,
                        row.getActionTaken(),
                        row.getId(),
                        modeWire
                ));
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

    /**
     * Blocks when the session already has {@code chat.session.max-requests} AI executions
     * (including superseded revisions).
     */
    @Transactional(readOnly = true)
    public void enforceSessionRequestLimit(String tenantId, String userId, String sessionId) {
        TenantUserSession resolved = resolveTenantUser(tenantId, userId);
        if (resolved == null) {
            return;
        }
        long count = requestLogRepository.countBySession_TenantAndSession_UserAndSession_SessionId(
                resolved.tenant(),
                resolved.user(),
                sessionId
        );
        if (count >= maxRequestsPerSession) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Session request limit reached (" + maxRequestsPerSession + ")"
            );
        }
    }

    /**
     * Validates that {@code editOfRequestLogId} is the latest active turn for this session.
     *
     * @return session PK for the atomic supersede UPDATE
     */
    @Transactional(readOnly = true)
    public Long validateLatestActiveEdit(
            String tenantId,
            String userId,
            String sessionId,
            Long editOfRequestLogId
    ) {
        if (editOfRequestLogId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "editOfRequestLogId is required");
        }

        RequestLog target = requestLogRepository.findById(editOfRequestLogId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found"));

        Session session = target.getSession();
        if (session == null
                || session.getTenant() == null
                || session.getUser() == null
                || !Objects.equals(session.getTenant().getTenantCode(), tenantId)
                || !Objects.equals(session.getUser().getUsername(), userId)
                || !Objects.equals(session.getSessionId(), sessionId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found");
        }

        if (target.getSupersededByRequestLogId() != null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Request has already been superseded"
            );
        }

        List<Long> latestActive = requestLogRepository.findActiveIdsBySessionOrderByIdDesc(
                session.getTenant(),
                session.getUser(),
                sessionId,
                PageRequest.of(0, 1)
        );
        if (latestActive.isEmpty() || !Objects.equals(latestActive.get(0), editOfRequestLogId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only the latest active request can be edited"
            );
        }

        return session.getId();
    }

    /**
     * Atomically supersedes {@code oldId} with {@code newId}.
     * On conflict (0 rows), hides the losing new revision under the winner, then returns false.
     * Caller must return 409 only after this method has persisted the hide.
     */
    @Transactional
    public boolean supersedeEditedRequest(Long oldId, Long newId, Long sessionPk) {
        int updated = requestLogRepository.supersedeIfActive(oldId, newId, sessionPk);
        if (updated == 1) {
            return true;
        }

        RequestLog oldRow = requestLogRepository.findById(oldId).orElse(null);
        Long winnerId = oldRow != null ? oldRow.getSupersededByRequestLogId() : null;
        if (winnerId == null) {
            RequestLog newRow = requestLogRepository.findById(newId).orElse(null);
            if (newRow != null && newRow.getSession() != null) {
                List<Long> active = requestLogRepository.findActiveIdsBySessionOrderByIdDesc(
                        newRow.getSession().getTenant(),
                        newRow.getSession().getUser(),
                        newRow.getSession().getSessionId(),
                        PageRequest.of(0, 1)
                );
                if (!active.isEmpty() && !Objects.equals(active.get(0), newId)) {
                    winnerId = active.get(0);
                }
            }
        }
        if (winnerId == null) {
            winnerId = oldId;
        }

        RequestLog loser = requestLogRepository.findById(newId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Edit conflict and losing revision not found"
                ));
        loser.setSupersededByRequestLogId(winnerId);
        requestLogRepository.saveAndFlush(loser);
        log.warn(
                "Edit supersede conflict: oldId={}, losingNewId={}, winnerId={}",
                oldId,
                newId,
                winnerId
        );
        return false;
    }

    static String toModeWireValue(String storedMode) {
        if (storedMode == null || storedMode.isBlank()) {
            return ChatMode.AUTO.getWireValue();
        }
        try {
            return ChatMode.valueOf(storedMode.trim().toUpperCase()).getWireValue();
        } catch (IllegalArgumentException ex) {
            return ChatMode.AUTO.getWireValue();
        }
    }

    private TenantUserSession resolveTenantUser(String tenantId, String userId) {
        Tenant tenant = tenantRepository.findByTenantCode(tenantId).orElse(null);
        if (tenant == null) {
            return null;
        }
        User user = userRepository.findByTenantAndUsername(tenant, userId).orElse(null);
        if (user == null) {
            return null;
        }
        return new TenantUserSession(tenant, user);
    }

    private record TenantUserSession(Tenant tenant, User user) {
    }
}