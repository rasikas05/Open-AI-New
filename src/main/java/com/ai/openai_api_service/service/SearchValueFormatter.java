package com.ai.openai_api_service.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SearchValueFormatter {

    private static final Set<String> DATE_FIELDS = Set.of(
            "ORDT", "PUDT", "RLDZ", "STDT", "FIDT", "RIDT"
    );

    private static final Pattern YYYY_MM_DD = Pattern.compile("^(\\d{4})-(\\d{2})-(\\d{2})$");
    private static final Pattern YYYY_SLASH_MM_DD = Pattern.compile("^(\\d{4})/(\\d{2})/(\\d{2})$");
    private static final Pattern YYYYMMDD = Pattern.compile("^(\\d{8})$");
    private static final Pattern YYMMDD = Pattern.compile("^(\\d{6})$");
    private static final Pattern DD_MM_YYYY = Pattern.compile("^(\\d{1,2})/(\\d{1,2})/(\\d{4})$");
    private static final Pattern DD_DASH_MM_YYYY = Pattern.compile("^(\\d{1,2})-(\\d{1,2})-(\\d{4})$");

    private final Map<String, UnaryOperator<String>> formatters;

    public SearchValueFormatter() {
        Map<String, UnaryOperator<String>> map = new java.util.HashMap<>();
        for (String field : DATE_FIELDS) {
            map.put(field, SearchValueFormatter::formatDate);
        }
        this.formatters = Map.copyOf(map);
    }

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
        Matcher m;

        m = YYYY_MM_DD.matcher(value);
        if (m.matches()) {
            return compact(m.group(1), m.group(2), m.group(3));
        }

        m = YYYY_SLASH_MM_DD.matcher(value);
        if (m.matches()) {
            return compact(m.group(1), m.group(2), m.group(3));
        }

        m = YYYYMMDD.matcher(value);
        if (m.matches()) {
            return m.group(1);
        }

        m = YYMMDD.matcher(value);
        if (m.matches()) {
            String yy = m.group(1).substring(0, 2);
            String mm = m.group(1).substring(2, 4);
            String dd = m.group(1).substring(4, 6);
            int year = Integer.parseInt(yy);
            String yyyy = String.valueOf(year >= 70 ? 1900 + year : 2000 + year);
            return compact(yyyy, mm, dd);
        }

        m = DD_MM_YYYY.matcher(value);
        if (m.matches()) {
            int first = Integer.parseInt(m.group(1));
            int second = Integer.parseInt(m.group(2));
            String year = m.group(3);
            if (second > 12) {
                // Must be MM/DD/YYYY
                return compact(year, pad2(first), pad2(second));
            }
            if (first > 12) {
                // Must be DD/MM/YYYY
                return compact(year, pad2(second), pad2(first));
            }
            // Ambiguous or clear DD/MM — prefer DD/MM/YYYY
            return compact(year, pad2(second), pad2(first));
        }

        m = DD_DASH_MM_YYYY.matcher(value);
        if (m.matches()) {
            int first = Integer.parseInt(m.group(1));
            int second = Integer.parseInt(m.group(2));
            String year = m.group(3);
            if (second > 12) {
                return compact(year, pad2(first), pad2(second));
            }
            if (first > 12) {
                return compact(year, pad2(second), pad2(first));
            }
            return compact(year, pad2(second), pad2(first));
        }

        return value;
    }

    private static String compact(String year, String month, String day) {
        return year + pad2(Integer.parseInt(month)) + pad2(Integer.parseInt(day));
    }

    private static String pad2(int value) {
        return value < 10 ? "0" + value : String.valueOf(value);
    }

    private static String pad2(String value) {
        return pad2(Integer.parseInt(value));
    }
}
