package com.ai.openai_api_service.repository;

import com.ai.openai_api_service.entity.RequestLog;
import com.ai.openai_api_service.entity.Tenant;
import com.ai.openai_api_service.entity.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

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

    Optional<RequestLog> findFirstBySession_SessionIdOrderByIdAsc(String sessionId);

    List<RequestLog> findBySession_SessionId(String sessionId);

    long countBySession_TenantAndSession_UserAndSession_SessionId(
            Tenant tenant,
            User user,
            String sessionId
    );

    @Query("""
            SELECT r FROM RequestLog r
            WHERE r.session.tenant = :tenant
              AND r.session.user = :user
              AND r.session.sessionId = :sessionId
              AND r.supersededByRequestLogId IS NULL
            ORDER BY r.createdAt DESC
            """)
    List<RequestLog> findActiveBySessionOrderByCreatedAtDesc(
            @Param("tenant") Tenant tenant,
            @Param("user") User user,
            @Param("sessionId") String sessionId,
            Pageable pageable
    );

    @Query("""
            SELECT r.id FROM RequestLog r
            WHERE r.session.tenant = :tenant
              AND r.session.user = :user
              AND r.session.sessionId = :sessionId
              AND r.supersededByRequestLogId IS NULL
            ORDER BY r.id DESC
            """)
    List<Long> findActiveIdsBySessionOrderByIdDesc(
            @Param("tenant") Tenant tenant,
            @Param("user") User user,
            @Param("sessionId") String sessionId,
            Pageable pageable
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE RequestLog r
            SET r.supersededByRequestLogId = :newId
            WHERE r.id = :oldId
              AND r.session.id = :sessionPk
              AND r.supersededByRequestLogId IS NULL
            """)
    int supersedeIfActive(
            @Param("oldId") Long oldId,
            @Param("newId") Long newId,
            @Param("sessionPk") Long sessionPk
    );
}
