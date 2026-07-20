package com.ai.openai_api_service.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

@Service
public class SearchValueFormatter {

    private static final Pattern ISO_DATE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
    private static final Pattern COMPACT_DATE = Pattern.compile("^\\d{8}$");

    private final Map<String, UnaryOperator<String>> formatters = Map.of(
            "ORDT", SearchValueFormatter::formatDate,
            "PUDT", SearchValueFormatter::formatDate
    );

    public String format(String field, String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        UnaryOperator<String> formatter = formatters.get(field);
        if (formatter == null) {
            return trimmed;
        }
        return formatter.apply(trimmed);
    }

    private static String formatDate(String value) {
        if (ISO_DATE.matcher(value).matches()) {
            return value.replace("-", "");
        }
        if (COMPACT_DATE.matcher(value).matches()) {
            return value;
        }
        return value;
    }
}
