package com.ai.openai_api_service.repository;

import com.ai.openai_api_service.entity.TokenTransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TokenTransactionRepository extends JpaRepository<TokenTransactionEntity, Long> {
}
