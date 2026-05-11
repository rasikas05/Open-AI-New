//package com.ai.openai_api_service.service;
package com.ai.openai_api_service.service;
import com.ai.openai_api_service.entity.RequestLog;
import com.ai.openai_api_service.entity.Session;
import com.ai.openai_api_service.entity.Tenant;
import com.ai.openai_api_service.entity.User;
import com.ai.openai_api_service.model.MessageDto;
import com.ai.openai_api_service.repository.RequestLogRepository;
import com.ai.openai_api_service.repository.SessionRepository;
import com.ai.openai_api_service.repository.TenantRepository;
import com.ai.openai_api_service.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
            UserRepository userRepository) {
        this.sessionRepository = sessionRepository;
        this.requestLogRepository = requestLogRepository;
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
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
            Tenant tenant = tenantRepository.findByTenantCode(tenantId).orElse(null);
            if (tenant == null) {
                log.warn("Tenant not found for persistence: tenantId={}", tenantId);
                return;
            }
            User user = userRepository.findByTenantAndUsername(tenant, userId).orElse(null);
            if (user == null) {
                log.warn("User not found for persistence: tenantId={}, userId={}", tenantId, userId);
                return;
            }

            Session session = sessionRepository.findByTenantAndUserAndSessionId(tenant, user, sessionId)
                    .orElse(null);

            if (session == null) {
                log.warn("Session not found for persistence: tenantId={}, userId={}, sessionId={}", tenantId, userId, sessionId);
                return;
            }

            int consumed = requestTokensUsed != null ? requestTokensUsed : 0;
            session.setTokensUsed(session.getTokensUsed() + consumed);
            session.setUpdatedAt(LocalDateTime.now());
            sessionRepository.save(session);

            RequestLog message = new RequestLog();
            message.setSession(session);
            message.setOriginalText(originalText);
            message.setSanitizedText(sanitizedText);
            message.setOpenaiResponse(openAiResponse);
            message.setTokensUsed(consumed);
            requestLogRepository.save(message);
        } catch (Exception e) {
            log.error("Failed to persist chat interaction. sessionId={}, reason={}", sessionId, e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public List<MessageDto> loadHistoryForPrompt(String tenantId, String userId, String sessionId, int maxExchanges) {
        if (maxExchanges <= 0) {
            return List.of();
        }

        Tenant tenant = tenantRepository.findByTenantCode(tenantId).orElse(null);
        if (tenant == null) {
            return List.of();
        }
        User user = userRepository.findByTenantAndUsername(tenant, userId).orElse(null);
        if (user == null) {
            return List.of();
        }

        List<RequestLog> rows = requestLogRepository.findBySession_TenantAndSession_UserAndSession_SessionIdOrderByCreatedAtDesc(
                tenant,
                user,
                sessionId,
                PageRequest.of(0, maxExchanges)
        );
        Collections.reverse(rows);

        List<MessageDto> messages = new ArrayList<>();
        for (RequestLog row : rows) {
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
    public List<RequestLog> loadHistoryForDisplay(String tenantId, String userId, String sessionId, int maxExchanges) {
        if (maxExchanges <= 0) {
            return List.of();
        }

        Tenant tenant = tenantRepository.findByTenantCode(tenantId).orElse(null);
        if (tenant == null) {
            return List.of();
        }
        User user = userRepository.findByTenantAndUsername(tenant, userId).orElse(null);
        if (user == null) {
            return List.of();
        }

        List<RequestLog> rows = requestLogRepository.findBySession_TenantAndSession_UserAndSession_SessionIdOrderByCreatedAtDesc(
                tenant,
                user,
                sessionId,
                PageRequest.of(0, maxExchanges)
        );
        Collections.reverse(rows);
        return rows;
    }

    @Transactional(readOnly = true)
    public List<Session> listSessions(String tenantId, String userId) {
        Tenant tenant = tenantRepository.findByTenantCode(tenantId).orElse(null);
        if (tenant == null) {
            return List.of();
        }
        User user = userRepository.findByTenantAndUsername(tenant, userId).orElse(null);
        if (user == null) {
            return List.of();
        }
        return sessionRepository.findByTenantAndUserOrderByUpdatedAtDesc(tenant, user);
    }

    @Transactional(readOnly = true)
    public long countSessions(String tenantId, String userId) {
        Tenant tenant = tenantRepository.findByTenantCode(tenantId).orElse(null);
        if (tenant == null) {
            return 0;
        }
        User user = userRepository.findByTenantAndUsername(tenant, userId).orElse(null);
        if (user == null) {
            return 0;
        }
        return sessionRepository.countByTenantAndUser(tenant, user);
    }

    @Transactional(readOnly = true)
    public List<RequestLog> loadSessionMessages(String tenantId, String userId, String sessionId) {
        Tenant tenant = tenantRepository.findByTenantCode(tenantId).orElse(null);
        if (tenant == null) {
            return List.of();
        }
        User user = userRepository.findByTenantAndUsername(tenant, userId).orElse(null);
        if (user == null) {
            return List.of();
        }
        return requestLogRepository.findBySession_TenantAndSession_UserAndSession_SessionIdOrderByCreatedAtAsc(
                tenant,
                user,
                sessionId
        );
    }

    @Transactional(readOnly = true)
    public Session getSessionById(String sessionId) {
        return sessionRepository.findBySessionId(sessionId).orElse(null);
    }

    @Transactional
    public Session closeSessionById(String sessionId) {
        return sessionRepository.findBySessionId(sessionId).map(session -> {
            session.setStatus("CLOSED");
            session.setEndTime(LocalDateTime.now());
            session.setUpdatedAt(LocalDateTime.now());
            return sessionRepository.save(session);
        }).orElse(null);
    }
}
