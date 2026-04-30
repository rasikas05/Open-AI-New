package com.ai.openai_api_service.repository;

import com.ai.openai_api_service.entity.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SessionRepository extends JpaRepository<Session, Long> {
    List<Session> findByTenantIdAndUserId(String tenantId, String userId);
    Optional<Session> findByTenantIdAndUserIdAndSessionId(String tenantId, String userId, String sessionId);
}