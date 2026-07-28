package com.ai.openai_api_service.service.api;

/**
 * Metadata for one M3 return column tied to a requested-information code.
 */
public record ApiField(
        String field,
        String label,
        boolean visible,
        String formatterKey
) {
    public ApiField {
        formatterKey = formatterKey != null && !formatterKey.isBlank()
                ? formatterKey
                : ApiFieldMetadata.DEFAULT;
    }

    public static ApiField of(String field, String label) {
        return new ApiField(field, label, true, ApiFieldMetadata.DEFAULT);
    }

    public static ApiField of(String field, String label, String formatterKey) {
        return new ApiField(field, label, true, formatterKey);
    }
}
