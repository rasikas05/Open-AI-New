package com.ai.openai_api_service.repository;

import com.ai.openai_api_service.entity.ChatAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ChatAttachmentRepository extends JpaRepository<ChatAttachment, Long> {

    List<ChatAttachment> findByTenantCodeAndMessageIdOrderByImageOrderAsc(
            String tenantCode,
            String messageId
    );

    List<ChatAttachment> findByTenantCodeAndRequestLogIdInOrderByImageOrderAsc(
            String tenantCode,
            Collection<Long> requestLogIds
    );

    Optional<ChatAttachment> findByTenantCodeAndIdmPid(String tenantCode, String idmPid);
}
