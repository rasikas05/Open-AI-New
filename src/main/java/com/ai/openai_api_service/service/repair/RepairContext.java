package com.ai.openai_api_service.service.repair;

import com.ai.openai_api_service.service.normalizer.SlotValue;
import com.ai.openai_api_service.service.validation.ValidatedSlot;

import java.util.List;
import java.util.Map;

public record RepairContext(
        String intentName,
        String userUtterance,
        Map<String, SlotValue> slots,
        List<ValidatedSlot> validatedSlots,
        List<IntentFieldDescriptor> intentFields
) {
}
