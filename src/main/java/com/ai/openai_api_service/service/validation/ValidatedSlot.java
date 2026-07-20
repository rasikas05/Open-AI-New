package com.ai.openai_api_service.service.validation;

public record ValidatedSlot(
        String lexSlotName,
        String m3Field,
        String value,
        boolean valid,
        String reason
) {
}
