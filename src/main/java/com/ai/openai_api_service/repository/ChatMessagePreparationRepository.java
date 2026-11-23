package com.ai.openai_api_service.repository;

import com.ai.openai_api_service.entity.ChatMessagePreparation;
import com.ai.openai_api_service.entity.ChatMessagePreparation.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface ChatMessagePreparationRepository extends JpaRepository<ChatMessagePreparation, String> {

    Optional<ChatMessagePreparation> findByMessageId(String messageId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update ChatMessagePreparation p
            set p.status = :consumed,
                p.consumedAt = :consumedAt
            where p.messageId = :messageId
              and p.status = :prepared
            """)
    int consumeIfPrepared(
            @Param("messageId") String messageId,
            @Param("prepared") Status prepared,
            @Param("consumed") Status consumed,
            @Param("consumedAt") Instant consumedAt
    );
}
