package com.ai.openai_api_service.repository;

import com.ai.openai_api_service.entity.Session;
import com.ai.openai_api_service.entity.Tenant;
import com.ai.openai_api_service.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SessionRepository extends JpaRepository<Session, Long> {

    List<Session> findByTenantAndUserOrderByUpdatedAtDesc(
            Tenant tenant,
            User user
    );

    Optional<Session> findByTenantAndUserAndSessionId(
            Tenant tenant,
            User user,
            String sessionId
    );

    Optional<Session> findBySessionId(String sessionId);

    long countByTenantAndUser(
            Tenant tenant,
            User user
    );
}