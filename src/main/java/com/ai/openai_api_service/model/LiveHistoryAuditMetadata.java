package com.ai.openai_api_service.model;

public record LiveHistoryAuditMetadata(
        String lexIntent,
        String businessObject,
        String businessIdentifier
) {
}
