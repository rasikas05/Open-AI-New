package com.ai.openai_api_service.service;

import com.ai.openai_api_service.model.SearchCriterion;
import com.ai.openai_api_service.model.lex.LexRecognizeResult;
import com.ai.openai_api_service.service.api.InformationRequestCatalog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
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
    public static final String CREDIT_LIMIT = "CREDIT_LIMIT";
    public static final String OUTSTANDING_INVOICES = "OUTSTANDING_INVOICES";
    public static final String OVERDUE_INVOICES = "OVERDUE_INVOICES";
    public static final String PAYMENT = "PAYMENT";
    public static final String INSURANCE = "INSURANCE";
    public static final String CURRENCY = "CURRENCY";
    public static final String VAT = "VAT";
    public static final String INVOICE_RECIPIENT = "INVOICE_RECIPIENT";
    public static final String PAYER = "PAYER";
    public static final String GROUP_PAYER = "GROUP_PAYER";

    /**
     * Intent-agnostic M3 search field → canonical business information group.
     * Used to suppress requested-information codes already represented by search criteria.
     */
    static final Map<String, String> BUSINESS_GROUP_BY_M3_FIELD = Map.copyOf(buildBusinessGroupByM3Field());

    private static final Pattern ADDRESS_PATTERN = Pattern.compile(
            "\\b(address(?!\\s+id\\b)|location|street|town|city|postal|zip)\\b",
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

    private final InformationRequestCatalog informationRequestCatalog;

    @Autowired
    public RequestedInformationResolver(
            SearchFieldCatalog searchFieldCatalog,
            InformationRequestCatalog informationRequestCatalog
    ) {
        // searchFieldCatalog retained for Spring wiring / future catalog-driven index refresh
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
     * SEARCH-only: detect display groups, normalize to business concepts, suppress groups already
     * used as search criteria, default FULL.
     */
    public List<String> resolveForSearch(String userText, List<SearchCriterion> searchCriteria) {
        List<PositionedCode> positioned = new ArrayList<>();

        int statusIndex = earliestStatusDisplayIndex(userText);
        if (statusIndex >= 0) {
            positioned.add(new PositionedCode(STATUS, statusIndex));
        }

        for (InformationRequestCatalog.MatchedCode matched : informationRequestCatalog.matchCodesWithPositions(userText)) {
            String code = normalizeBusinessGroup(matched.code());
            positioned.add(new PositionedCode(code, matched.startIndex()));
        }

        positioned.sort(Comparator.comparingInt(PositionedCode::startIndex));

        Set<String> requestedGroups = new LinkedHashSet<>();
        for (PositionedCode item : positioned) {
            requestedGroups.add(normalizeBusinessGroup(item.code()));
        }

        if (requestedGroups.isEmpty()) {
            return List.of(FULL);
        }

        Set<String> criteriaGroups = new LinkedHashSet<>();
        if (searchCriteria != null) {
            for (SearchCriterion criterion : searchCriteria) {
                if (criterion == null || criterion.field() == null || criterion.field().isBlank()) {
                    continue;
                }
                String group = BUSINESS_GROUP_BY_M3_FIELD.get(criterion.field().trim().toUpperCase(Locale.ROOT));
                if (group != null) {
                    criteriaGroups.add(group);
                }
            }
        }

        requestedGroups.removeAll(criteriaGroups);

        // Specific status variants win over generic STATUS when both matched
        if (requestedGroups.contains("LOWEST_STATUS")) {
            requestedGroups.remove(STATUS);
        }
        // Address id must not also pull GetCustomer ADDRESS columns
        if (requestedGroups.contains("ADDRESS_ID")) {
            requestedGroups.remove(ADDRESS);
        }

        if (requestedGroups.isEmpty()) {
            return List.of(FULL);
        }
        return List.copyOf(requestedGroups);
    }

    /**
     * Collapse InformationRequestCatalog aliases to canonical business groups used by
     * {@link #BUSINESS_GROUP_BY_M3_FIELD} and ApiFieldCatalog.
     */
    static String normalizeBusinessGroup(String code) {
        if (code == null || code.isBlank()) {
            return code;
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "HIGHEST_STATUS", "ORDER_STATUS", "PURCHASE_STATUS" -> STATUS;
            case "CUSTOMER_NUMBER" -> "CUSTOMER";
            case "SUPPLIER_NUMBER" -> "SUPPLIER";
            case "PRODUCT" -> "PRODUCT_NUMBER";
            case "REQUESTED_DELIVERY_DATE", "REQUESTED_DELIVERY" -> "DELIVERY_DATE";
            default -> normalized;
        };
    }

    /**
     * Earliest index of a status-display phrase in the utterance, or -1 if none.
     */
    static int earliestStatusDisplayIndex(String userText) {
        if (userText == null || userText.isBlank()) {
            return -1;
        }
        String text = userText.trim();
        int earliest = -1;

        Matcher orderStatus = SEARCH_ORDER_STATUS.matcher(text);
        if (orderStatus.find()) {
            earliest = orderStatus.start();
        }

        Matcher showStatus = SEARCH_SHOW_STATUS.matcher(text);
        if (showStatus.find()) {
            int idx = text.toLowerCase(Locale.ROOT).indexOf("status", showStatus.start());
            if (idx >= 0 && (earliest < 0 || idx < earliest)) {
                earliest = idx;
            }
        }

        if (SEARCH_DISPLAY_LEAD.matcher(text).find()) {
            Matcher trailingStatus = Pattern.compile("\\bstatus\\s*$", Pattern.CASE_INSENSITIVE).matcher(text);
            if (trailingStatus.find() && (earliest < 0 || trailingStatus.start() < earliest)) {
                earliest = trailingStatus.start();
            }
        }

        return earliest;
    }

    private record PositionedCode(String code, int startIndex) {
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

    private static Map<String, String> buildBusinessGroupByM3Field() {
        Map<String, String> map = new LinkedHashMap<>();
        putFields(map, STATUS, "ORST", "PUST", "WHST", "TRSH");
        putFields(map, "LOWEST_STATUS", "ORSL", "PUSL", "TRSL");
        putFields(map, "CUSTOMER", "CUNO");
        putFields(map, "SUPPLIER", "SUNO");
        putFields(map, "FACILITY", "FACI");
        putFields(map, "WAREHOUSE", "WHLO");
        putFields(map, "RESPONSIBLE", "RESP");
        putFields(map, "SALESPERSON", "SMCD");
        putFields(map, "BUYER", "BUYE");
        putFields(map, "ORDER_TYPE", "ORTP", "ORTY", "TRTP");
        putFields(map, "ORDER_DATE", "ORDT", "PUDT");
        putFields(map, "PRODUCT_NUMBER", "PRNO");
        putFields(map, "PRIORITY", "PRIO");
        putFields(map, "ORDER_NUMBER", "ORNO");
        putFields(map, "PURCHASE_ORDER_NUMBER", "PUNO");
        putFields(map, "MANUFACTURING_ORDER_NUMBER", "MFNO");
        putFields(map, "DISTRIBUTION_ORDER_NUMBER", "TRNR");
        putFields(map, "DIVISION", "DIVI");
        putFields(map, "PURCHASE_CATEGORY", "POTC");
        putFields(map, "RECEIVING_DATE", "RIDT");
        putFields(map, "PLANNED_START_DATE", "STDT");
        putFields(map, "PLANNED_FINISH_DATE", "FIDT");
        putFields(map, "REFERENCE_ORDER_NUMBER", "RORN");
        putFields(map, PAYER, "PYNO");
        putFields(map, "REQUISITION_BY", "PURC");
        putFields(map, "DELIVERY_DATE", "RLDZ");
        return map;
    }

    private static void putFields(Map<String, String> map, String group, String... m3Fields) {
        for (String field : m3Fields) {
            map.put(field, group);
        }
    }

    private List<String> mergeLegacyAndCatalog(String userText) {
        if (userText == null || userText.isBlank()) {
            return List.of();
        }
        Set<String> groups = new LinkedHashSet<>(parseFromText(userText));
        groups.addAll(informationRequestCatalog.matchCodesFromUtterance(userText));
        if (groups.contains("ADDRESS_ID")) {
            groups.remove(ADDRESS);
        }
        if (groups.contains("LOWEST_STATUS")) {
            groups.remove(STATUS);
        }
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
