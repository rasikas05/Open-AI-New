package com.ai.openai_api_service.repository;

import com.ai.openai_api_service.entity.ChatSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChatSessionRepository extends JpaRepository<ChatSessionEntity, Long> {
    Optional<ChatSessionEntity> findBySessionId(String sessionId);
}
