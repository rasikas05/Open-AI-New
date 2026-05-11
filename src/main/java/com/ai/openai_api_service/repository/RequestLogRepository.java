package com.ai.openai_api_service.repository;

import com.ai.openai_api_service.entity.RequestLog;
import com.ai.openai_api_service.entity.Tenant;
import com.ai.openai_api_service.entity.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RequestLogRepository extends JpaRepository<RequestLog, Long> {
    List<RequestLog> findBySession_TenantAndSession_UserAndSession_SessionIdOrderByCreatedAtDesc(
            Tenant tenant,
            User user,
            String sessionId,
            Pageable pageable
    );

    List<RequestLog> findBySession_TenantAndSession_UserAndSession_SessionIdOrderByCreatedAtAsc(
            Tenant tenant,
            User user,
            String sessionId
    );

    List<RequestLog> findBySession_SessionId(String sessionId);
}