package com.ai.openai_api_service.repository;

import com.ai.openai_api_service.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, Long> {

    List<Tenant> findByStatus(String status);

    Optional<Tenant> findByTenantCode(String tenantCode);

    boolean existsByTenantCode(String tenantCode);
    Optional<Tenant> findByTenantCodeIgnoreCase(String tenantCode);

    boolean existsByTenantCodeIgnoreCase(String tenantCode);
}