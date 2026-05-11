package com.ai.openai_api_service.repository;

import com.ai.openai_api_service.entity.Tenant;
import com.ai.openai_api_service.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    List<User> findByTenant(Tenant tenant);
    Optional<User> findByTenantAndUsername(Tenant tenant, String username);
}