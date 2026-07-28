package com.ai.openai_api_service.service.api;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Global M3 field display formatting (metadata-driven).
 */
@Component
public class ApiFieldMetadata {

    public static final String DEFAULT = "DEFAULT";
    public static final String AMOUNT = "AMOUNT";
    public static final String CODE = "CODE";
    public static final String DATE = "DATE";
    public static final String BOOLEAN = "BOOLEAN";
    public static final String STATUS = "STATUS";

    private final Map<String, Function<Object, String>> formattersByKey;

    public ApiFieldMetadata() {
        Map<String, Function<Object, String>> formatters = new LinkedHashMap<>();
        formatters.put(DEFAULT, ApiFieldMetadata::formatDefault);
        formatters.put(AMOUNT, ApiFieldMetadata::formatDefault);
        formatters.put(CODE, ApiFieldMetadata::formatDefault);
        formatters.put(DATE, ApiFieldMetadata::formatDefault);
        formatters.put(BOOLEAN, ApiFieldMetadata::formatDefault);
        formatters.put(STATUS, ApiFieldMetadata::formatDefault);
        this.formattersByKey = Map.copyOf(formatters);
    }

    public String format(String formatterKey, Object value) {
        if (value == null) {
            return "";
        }
        String key = formatterKey != null && !formatterKey.isBlank() ? formatterKey : DEFAULT;
        Function<Object, String> formatter = formattersByKey.getOrDefault(key, formattersByKey.get(DEFAULT));
        return formatter.apply(value).trim();
    }

    private static String formatDefault(Object value) {
        return String.valueOf(value).trim();
    }

    public static String titleCaseFieldName(String m3Field) {
        if (m3Field == null || m3Field.isBlank()) {
            return "";
        }
        return m3Field.trim().toUpperCase(Locale.ROOT);
    }
}
