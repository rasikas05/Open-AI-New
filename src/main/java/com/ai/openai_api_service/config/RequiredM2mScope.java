package com.ai.openai_api_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Exposes the Cognito M2M custom scope as a Spring Security authority for SpEL:
 * {@code @PreAuthorize("hasAuthority(@requiredM2mScope.authority)")}.
 */
@Component("requiredM2mScope")
public class RequiredM2mScope {

    private final String authority;

    public RequiredM2mScope(@Value("${aws.cognito.required-scope}") String requiredScope) {
        if (requiredScope == null || requiredScope.isBlank()) {
            throw new IllegalArgumentException("aws.cognito.required-scope must be set");
        }
        String trimmed = requiredScope.trim();
        this.authority = trimmed.startsWith("SCOPE_") ? trimmed : "SCOPE_" + trimmed;
    }

    public String getAuthority() {
        return authority;
    }
}
