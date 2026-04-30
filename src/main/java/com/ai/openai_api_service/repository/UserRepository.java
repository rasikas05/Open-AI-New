package com.ai.openai_api_service.repository;

import com.ai.openai_api_service.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    List<User> findByTenantId(String tenantId);
    Optional<User> findByTenantIdAndUserId(String tenantId, String userId);
}