package com.ai.openai_api_service.service.protection;

/**
 * Immutable execution metadata for a protection invocation.
 * Mutable texts / maps belong on {@link ProtectionSession} (Decision #21).
 */
public record ProtectionContext(
        ProtectionPurpose purpose,
        String tenantCode,
        boolean debug,
        String policyVersion,
        boolean enabled
) {
    public static ProtectionContext forPurpose(ProtectionPurpose purpose) {
        return forPurpose(purpose, true);
    }

    public static ProtectionContext forPurpose(ProtectionPurpose purpose, boolean enabled) {
        return new ProtectionContext(purpose, null, false, "V1", enabled);
    }
}
