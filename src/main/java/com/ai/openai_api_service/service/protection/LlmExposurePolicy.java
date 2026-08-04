package com.ai.openai_api_service.service.protection;

public enum LlmExposurePolicy {
    ALLOW,
    MASK,
    REPLACE,
    BLOCK,
    REVIEW
}
