package com.ai.openai_api_service.repository;

import com.ai.openai_api_service.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, Long> {
    List<Tenant> findAll();
    List<Tenant> findByStatus(String status);
    Optional<Tenant> findByTenantId(String tenantId);
    boolean existsByTenantId(String tenantId);
}