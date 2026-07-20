package com.ai.openai_api_service.model;

public record IntentDefinition(
        String intentName,
        String program,
        String transaction,
        RequestType requestType,
        String primaryParameter
) {
}
