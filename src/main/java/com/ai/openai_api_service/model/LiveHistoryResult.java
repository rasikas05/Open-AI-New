package com.ai.openai_api_service.model;

public record LiveHistoryResult(
        String summaryText,
        LiveHistoryAuditMetadata auditMetadata
) {
}
