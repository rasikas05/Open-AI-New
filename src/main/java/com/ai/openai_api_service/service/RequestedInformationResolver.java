package com.ai.openai_api_service.service;

import com.ai.openai_api_service.model.lex.LexRecognizeResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class RequestedInformationResolver {

    public static final String ADDRESS = "ADDRESS";
    public static final String PHONE = "PHONE";
    public static final String EMAIL = "EMAIL";
    public static final String STATUS = "STATUS";
    public static final String BASIC = "BASIC";
    public static final String FULL = "FULL";

    private static final Pattern ADDRESS_PATTERN = Pattern.compile(
            "\\b(address|location|street|town|city|postal|zip)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "\\b(phone|mobile|telephone|fax|tel)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "\\b(e-?mail|mail)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern STATUS_PATTERN = Pattern.compile(
            "\\b(status|blocked|active|inactive)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern BASIC_PATTERN = Pattern.compile(
            "\\b(basic|name|identity)\\b",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Resolves abstract display groups from the current user text and/or Lex session attributes.
     * Specific keyword groups win over FULL; otherwise prior session attrs; otherwise FULL.
     */
    public List<String> resolve(String userText, String lexIntent, Map<String, String> sessionAttributes) {
        List<String> fromText = parseFromText(userText);
        List<String> specific = fromText.stream()
                .filter(g -> !FULL.equals(g))
                .toList();

        if (!specific.isEmpty()) {
            return List.copyOf(specific);
        }

        List<String> fromSession = decode(sessionAttributes);
        if (!fromSession.isEmpty()) {
            return fromSession;
        }

        return List.of(FULL);
    }

    public String encode(List<String> groups) {
        if (groups == null || groups.isEmpty()) {
            return FULL;
        }
        return groups.stream()
                .filter(g -> g != null && !g.isBlank())
                .map(g -> g.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .sorted()
                .collect(Collectors.joining(","));
    }

    public List<String> decode(Map<String, String> sessionAttributes) {
        if (sessionAttributes == null || sessionAttributes.isEmpty()) {
            return List.of();
        }
        String raw = sessionAttributes.get(LexRecognizeResult.ATTR_REQUESTED_INFORMATION);
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        Set<String> groups = new LinkedHashSet<>();
        for (String part : raw.split(",")) {
            String code = part.trim().toUpperCase(Locale.ROOT);
            if (!code.isEmpty()) {
                groups.add(code);
            }
        }
        return List.copyOf(groups);
    }

    public boolean differsFromSession(List<String> resolved, Map<String, String> sessionAttributes) {
        List<String> existing = decode(sessionAttributes);
        if (resolved == null || resolved.isEmpty()) {
            return !existing.isEmpty();
        }
        if (existing.isEmpty()) {
            return true;
        }
        return !encode(resolved).equals(encode(existing));
    }

    private List<String> parseFromText(String userText) {
        if (userText == null || userText.isBlank()) {
            return List.of();
        }
        String text = userText.trim();
        List<String> groups = new ArrayList<>();
        if (ADDRESS_PATTERN.matcher(text).find()) {
            groups.add(ADDRESS);
        }
        if (PHONE_PATTERN.matcher(text).find()) {
            groups.add(PHONE);
        }
        if (EMAIL_PATTERN.matcher(text).find()) {
            groups.add(EMAIL);
        }
        if (STATUS_PATTERN.matcher(text).find()) {
            groups.add(STATUS);
        }
        if (BASIC_PATTERN.matcher(text).find()) {
            groups.add(BASIC);
        }
        return groups;
    }
}
