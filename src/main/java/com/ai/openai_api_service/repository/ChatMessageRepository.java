package com.ai.openai_api_service.repository;

import com.ai.openai_api_service.entity.ChatMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, Long> {
    List<ChatMessageEntity> findByTenantIdAndUserIdAndSessionIdOrderByCreatedAtDesc(
            String tenantId,
            String userId,
            String sessionId,
            Pageable pageable
    );

    List<ChatMessageEntity> findByTenantIdAndUserIdAndSessionIdOrderByCreatedAtAsc(
            String tenantId,
            String userId,
            String sessionId
    );
}
