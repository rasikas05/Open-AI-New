package com.ai.openai_api_service.service.repair;

import com.ai.openai_api_service.service.normalizer.FieldDefinition;
import com.ai.openai_api_service.service.normalizer.SlotValue;
import com.ai.openai_api_service.service.validation.ValidatedSlot;

import java.util.List;
import java.util.Map;

public record IntentFieldDescriptor(
        String lexSlotName,
        String m3Field,
        FieldDefinition definition
) {
}
