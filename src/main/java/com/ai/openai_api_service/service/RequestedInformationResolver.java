package com.ai.openai_api_service.service;

import com.ai.openai_api_service.model.SearchCriterion;
import com.ai.openai_api_service.model.SearchFieldDefinition;
import com.ai.openai_api_service.model.lex.LexRecognizeResult;
import com.ai.openai_api_service.service.api.InformationRequestCatalog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
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

    private static final List<String> SEARCH_INTENTS_FOR_FIELD_INDEX = List.of(
            "SearchCustomerOrder",
            "SearchPurchaseOrder",
            "SearchManufacturingOrder",
            "SearchDistributionOrder"
    );

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

    private static final Pattern SEARCH_DISPLAY_LEAD = Pattern.compile(
            "\\b(show|display|what is)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern SEARCH_ORDER_STATUS = Pattern.compile(
            "\\border status\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern SEARCH_SHOW_STATUS = Pattern.compile(
            "\\b(show|display)\\b[\\s\\S]*\\bstatus\\b",
            Pattern.CASE_INSENSITIVE
    );

    private final SearchFieldCatalog searchFieldCatalog;
    private final InformationRequestCatalog informationRequestCatalog;
    private volatile Map<String, String> m3FieldToDisplayGroupCache;

    @Autowired
    public RequestedInformationResolver(
            SearchFieldCatalog searchFieldCatalog,
            InformationRequestCatalog informationRequestCatalog
    ) {
        this.searchFieldCatalog = searchFieldCatalog;
        this.informationRequestCatalog = informationRequestCatalog;
    }

    /**
     * Resolves abstract display groups from the current user text and/or Lex session attributes.
     * Used for READ intents and elicit-slot flows. Specific keyword groups win over FULL.
     */
    public List<String> resolve(String userText, String lexIntent, Map<String, String> sessionAttributes) {
        List<String> fromText = mergeLegacyAndCatalog(userText);
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

    /**
     * SEARCH-only: detect explicit display groups, suppress groups tied to search criteria fields, default FULL.
     */
    public List<String> resolveForSearch(String userText, List<SearchCriterion> searchCriteria) {
        Set<String> candidates = new LinkedHashSet<>(parseSearchDisplayFromText(userText));
        for (String code : informationRequestCatalog.matchCodesFromUtterance(userText)) {
            candidates.add(normalizeSearchInformationCode(code));
        }
        if (candidates.isEmpty()) {
            return List.of(FULL);
        }

        Map<String, String> fieldToGroup = m3FieldToDisplayGroup();
        Set<String> criterionFields = searchCriteria == null
                ? Set.of()
                : searchCriteria.stream()
                .map(SearchCriterion::field)
                .filter(f -> f != null && !f.isBlank())
                .collect(Collectors.toSet());

        candidates.removeIf(group -> criterionFields.stream()
                .anyMatch(field -> group.equals(fieldToGroup.get(field))));

        if (candidates.isEmpty()) {
            return List.of(FULL);
        }
        return List.copyOf(candidates);
    }

    private static String normalizeSearchInformationCode(String code) {
        if ("ORDER_STATUS".equalsIgnoreCase(code)) {
            return STATUS;
        }
        return code;
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

    private List<String> parseSearchDisplayFromText(String userText) {
        if (userText == null || userText.isBlank()) {
            return List.of();
        }
        String text = userText.trim();
        List<String> groups = new ArrayList<>();

        if (detectSearchStatusDisplay(text)) {
            groups.add(STATUS);
        }

        return groups;
    }

    private static boolean detectSearchStatusDisplay(String text) {
        if (SEARCH_ORDER_STATUS.matcher(text).find()) {
            return true;
        }
        if (SEARCH_SHOW_STATUS.matcher(text).find()) {
            return true;
        }
        if (SEARCH_DISPLAY_LEAD.matcher(text).find() && Pattern.compile("\\bstatus\\s*$", Pattern.CASE_INSENSITIVE)
                .matcher(text)
                .find()) {
            return true;
        }
        return false;
    }

    private Map<String, String> m3FieldToDisplayGroup() {
        Map<String, String> cached = m3FieldToDisplayGroupCache;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (m3FieldToDisplayGroupCache == null) {
                m3FieldToDisplayGroupCache = Map.copyOf(buildM3FieldToDisplayGroup());
            }
            return m3FieldToDisplayGroupCache;
        }
    }

    private Map<String, String> buildM3FieldToDisplayGroup() {
        Map<String, String> map = new LinkedHashMap<>();
        for (String intentName : SEARCH_INTENTS_FOR_FIELD_INDEX) {
            for (SearchFieldDefinition field : searchFieldCatalog.fieldsFor(intentName)) {
                if (field.m3Field() == null || field.m3Field().isBlank()) {
                    continue;
                }
                if (keywordsIndicateStatus(field.keywords())) {
                    map.putIfAbsent(field.m3Field(), STATUS);
                }
            }
        }
        return map;
    }

    private static boolean keywordsIndicateStatus(List<String> keywords) {
        if (keywords == null) {
            return false;
        }
        for (String keyword : keywords) {
            if (keyword != null && keyword.toLowerCase(Locale.ROOT).contains("status")) {
                return true;
            }
        }
        return false;
    }

    private List<String> mergeLegacyAndCatalog(String userText) {
        if (userText == null || userText.isBlank()) {
            return List.of();
        }
        Set<String> groups = new LinkedHashSet<>(parseFromText(userText));
        groups.addAll(informationRequestCatalog.matchCodesFromUtterance(userText));
        return List.copyOf(groups);
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
