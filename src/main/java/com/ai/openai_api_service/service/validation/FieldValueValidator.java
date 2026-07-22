package com.ai.openai_api_service.service.validation;

import com.ai.openai_api_service.service.normalizer.FieldDefinition;

/**
 * Shared M3 field value format checks used by slot validation and M3 request execution validation.
 */
public final class FieldValueValidator {

    private FieldValueValidator() {
    }

    public static boolean isValid(FieldDefinition definition, String value) {
        return validate(definition, value).passed();
    }

    public static ValidationOutcome validate(FieldDefinition definition, String value) {
        if (definition == null || value == null) {
            return ValidationOutcome.fail("Missing field definition or value");
        }

        if (!definition.allowSpaces() && value.contains(" ")) {
            return ValidationOutcome.fail("Spaces are not allowed");
        }

        if (definition.expectedLength() != null && value.length() != definition.expectedLength()) {
            return ValidationOutcome.fail(
                    "Expected length=" + definition.expectedLength() + ", received=" + value.length()
            );
        }

        if (definition.minLength() != null && value.length() < definition.minLength()) {
            return ValidationOutcome.fail(
                    "Minimum length=" + definition.minLength() + ", received=" + value.length()
            );
        }

        if (definition.maxLength() != null && value.length() > definition.maxLength()) {
            return ValidationOutcome.fail(
                    "Maximum length=" + definition.maxLength() + ", received=" + value.length()
            );
        }

        if (definition.regexPattern() != null && !definition.regexPattern().matcher(value).matches()) {
            return ValidationOutcome.fail("Value does not match expected pattern for " + definition.fieldType());
        }

        return ValidationOutcome.ok();
    }

    public record ValidationOutcome(boolean passed, String reason) {
        public static ValidationOutcome ok() {
            return new ValidationOutcome(true, null);
        }

        public static ValidationOutcome fail(String reason) {
            return new ValidationOutcome(false, reason);
        }
    }
}
