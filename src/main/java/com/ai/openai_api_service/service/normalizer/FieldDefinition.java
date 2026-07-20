package com.ai.openai_api_service.service.normalizer;

import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Metadata describing an M3 search field for normalization, validation, and repair.
 */
public record FieldDefinition(
        String fieldName,
        CaseStrategy caseStrategy,
        FieldType fieldType,
        FieldRole repairRole,
        Pattern regexPattern,
        Integer expectedLength,
        Integer minLength,
        Integer maxLength,
        boolean allowSpaces,
        Function<String, String> formatter
) {
}
