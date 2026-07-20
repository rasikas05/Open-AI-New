package com.ai.openai_api_service.service.repair;

public record RepairAction(
        String lexSlotName,
        String oldValue,
        String newValue,
        String reason,
        double confidence
) {
}
