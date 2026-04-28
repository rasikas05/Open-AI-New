package com.ai.openai_api_service.repository;

import com.ai.openai_api_service.entity.ChatSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChatSessionRepository extends JpaRepository<ChatSessionEntity, Long> {
    Optional<ChatSessionEntity> findBySessionId(String sessionId);

    Optional<ChatSessionEntity> findByTenantIdAndUserIdAndSessionId(String tenantId, String userId, String sessionId);

    List<ChatSessionEntity> findByTenantIdAndUserIdOrderByUpdatedAtDesc(String tenantId, String userId);

    long countByTenantIdAndUserId(String tenantId, String userId);

    default Optional<ChatSessionEntity> findBySessionIdAllowNullTenantUser(String sessionId) {
        return findBySessionId(sessionId);
    }
}
