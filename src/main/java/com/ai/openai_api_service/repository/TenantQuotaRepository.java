package com.ai.openai_api_service.repository;

import com.ai.openai_api_service.entity.TenantQuota;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TenantQuotaRepository extends JpaRepository<TenantQuota, Long> {
    Optional<TenantQuota> findByTenantId(String tenantId);
    List<TenantQuota> findByStatus(String status);

    @Modifying
    @Query(value = """
            UPDATE tenant_quota
            SET tokens_used = tokens_used + :tokens
            WHERE tenant_id = :tenantId
              AND status = 'ACTIVE'
              AND (tokens_used + :tokens) <= (base_limit + extra_tokens)
            """, nativeQuery = true)
    int incrementUsageIfWithinLimit(@Param("tenantId") String tenantId, @Param("tokens") int tokens);
}
