package com.ai.openai_api_service.service.api;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Metadata for information request codes: display names and utterance keyword patterns.
 */
@Component
public class InformationRequestCatalog {

    public record InformationRequestDefinition(
            String code,
            String displayName,
            List<Pattern> keywordPatterns
    ) {
    }

    private static final Pattern INFORMATION_SEEKING_LEAD = Pattern.compile(
            "\\b(show|display|what is|get|tell me)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private final Map<String, InformationRequestDefinition> byCode;

    public InformationRequestCatalog() {
        this.byCode = Map.copyOf(seed());
    }

    public InformationRequestDefinition find(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return byCode.get(code.trim().toUpperCase(Locale.ROOT));
    }

    public String displayNameFor(String code) {
        InformationRequestDefinition def = find(code);
        return def != null ? def.displayName() : code;
    }

    public List<String> matchCodesFromUtterance(String userText) {
        if (userText == null || userText.isBlank()) {
            return List.of();
        }
        String text = userText.trim();
        List<String> matched = new ArrayList<>();
        for (InformationRequestDefinition def : byCode.values()) {
            if (matchesAny(text, def.keywordPatterns())) {
                matched.add(def.code());
            }
        }
        return List.copyOf(matched);
    }

    public boolean looksLikeUnknownSpecificRequest(String userText, List<String> alreadyResolvedCodes) {
        if (userText == null || userText.isBlank()) {
            return false;
        }
        if (alreadyResolvedCodes != null && !alreadyResolvedCodes.isEmpty()) {
            return false;
        }
        if (!INFORMATION_SEEKING_LEAD.matcher(userText).find()) {
            return false;
        }
        return matchCodesFromUtterance(userText).isEmpty();
    }

    private static boolean matchesAny(String text, List<Pattern> patterns) {
        for (Pattern pattern : patterns) {
            if (pattern.matcher(text).find()) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, InformationRequestDefinition> seed() {
        Map<String, InformationRequestDefinition> map = new LinkedHashMap<>();
        put(map, "SALESPERSON", "salesperson", "\\bsalesperson\\b", "\\bsales rep\\b", "\\bhandled by\\b");
        put(map, "DELIVERY_DATE", "delivery date", "\\bdelivery date\\b", "\\bdelivery\\s+date\\b");
        put(map, "ORDER_AMOUNT", "order amount", "\\border amount\\b");
        put(map, "ORDER_STATUS", "order status", "\\border status\\b");
        put(map, "ORDER_NUMBER", "order number", "\\border number\\b", "\\border no\\b");
        put(map, "LOYALTY_TIER", "loyalty tier", "\\bloyalty\\b", "\\bloyalty tier\\b");
        return map;
    }

    private static void put(
            Map<String, InformationRequestDefinition> map,
            String code,
            String displayName,
            String... regexes
    ) {
        List<Pattern> patterns = new ArrayList<>();
        for (String regex : regexes) {
            patterns.add(Pattern.compile(regex, Pattern.CASE_INSENSITIVE));
        }
        map.put(code, new InformationRequestDefinition(code, displayName, List.copyOf(patterns)));
    }
}
