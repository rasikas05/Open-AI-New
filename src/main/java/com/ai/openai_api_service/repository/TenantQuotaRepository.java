package com.ai.openai_api_service.repository;

import com.ai.openai_api_service.entity.TenantQuotaEntity;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TenantQuotaRepository extends JpaRepository<TenantQuotaEntity, Long> {
    Optional<TenantQuotaEntity> findByTenantId(String tenantId);

    List<TenantQuotaEntity> findByStatus(String status);

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
