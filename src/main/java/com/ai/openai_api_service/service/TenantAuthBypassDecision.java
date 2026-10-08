package com.ai.openai_api_service.service;

/**
 * Outcome of evaluating temporary tenant auth bypass for a request tenant code.
 */
public enum TenantAuthBypassDecision {
    ALLOWED,
    NOT_ALLOWLISTED,
    TENANT_MISSING_OR_INACTIVE
}
