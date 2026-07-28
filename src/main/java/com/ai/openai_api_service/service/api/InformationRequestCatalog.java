package com.ai.openai_api_service.service.api;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Metadata for information request codes: display names, priority, and utterance keyword patterns.
 */
@Component
public class InformationRequestCatalog {

    public record InformationRequestDefinition(
            String code,
            String displayName,
            List<Pattern> keywordPatterns,
            int priority
    ) {
    }

    /**
     * A matched information code with its earliest start index in the utterance.
     */
    public record MatchedCode(String code, int startIndex) {
    }

    private record SpanMatch(String code, int start, int end, int priority) {
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

    /**
     * Match catalog codes from utterance, ordered by earliest appearance in the text.
     */
    public List<String> matchCodesFromUtterance(String userText) {
        return matchCodesWithPositions(userText).stream()
                .map(MatchedCode::code)
                .toList();
    }

    /**
     * Match catalog codes with earliest start index; higher priority wins overlapping spans.
     */
    public List<MatchedCode> matchCodesWithPositions(String userText) {
        if (userText == null || userText.isBlank()) {
            return List.of();
        }
        String text = userText.trim();
        List<SpanMatch> spans = new ArrayList<>();
        for (InformationRequestDefinition def : byCode.values()) {
            SpanMatch span = earliestSpanMatch(text, def);
            if (span != null) {
                spans.add(span);
            }
        }

        spans.sort(Comparator
                .comparingInt(SpanMatch::start)
                .thenComparing(Comparator.comparingInt(SpanMatch::priority).reversed()));

        List<SpanMatch> selected = new ArrayList<>();
        for (SpanMatch candidate : spans) {
            boolean blocked = selected.stream().anyMatch(existing ->
                    existing.priority() > candidate.priority()
                            && rangesOverlap(existing, candidate));
            if (blocked) {
                continue;
            }
            selected.removeIf(existing ->
                    existing.priority() < candidate.priority()
                            && rangesOverlap(existing, candidate));
            if (selected.stream().noneMatch(s -> s.code().equals(candidate.code()))) {
                selected.add(candidate);
            }
        }

        selected.sort(Comparator.comparingInt(SpanMatch::start));
        List<MatchedCode> matched = new ArrayList<>();
        for (SpanMatch span : selected) {
            matched.add(new MatchedCode(span.code(), span.start()));
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

    private static boolean rangesOverlap(SpanMatch a, SpanMatch b) {
        return a.start() < b.end() && b.start() < a.end();
    }

    private static SpanMatch earliestSpanMatch(String text, InformationRequestDefinition def) {
        int earliestStart = -1;
        int earliestEnd = -1;
        for (Pattern pattern : def.keywordPatterns()) {
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                int start = matcher.start();
                if (earliestStart < 0 || start < earliestStart) {
                    earliestStart = start;
                    earliestEnd = matcher.end();
                }
            }
        }
        if (earliestStart < 0) {
            return null;
        }
        return new SpanMatch(def.code(), earliestStart, earliestEnd, def.priority());
    }

    private static Map<String, InformationRequestDefinition> seed() {
        Map<String, InformationRequestDefinition> map = new LinkedHashMap<>();
        put(map, "GROUP_PAYER", "group payer", 100, "\\bgroup\\s+payer\\b");
        put(map, "PAYMENT_TERMS", "payment terms", 90, "\\bpayment terms?\\b", "\\bpay terms?\\b");
        put(map, "INVOICE_RECIPIENT", "invoice recipient", 80,
                "\\binvoice\\s+recipient\\b", "\\binvoice\\s+receiver\\b");
        put(map, "OUTSTANDING_INVOICES", "outstanding invoices", 70,
                "\\boutstanding\\s+invoices?\\b", "\\boutstanding\\s+amount\\b");
        put(map, "OVERDUE_INVOICES", "overdue invoices", 70,
                "\\boverdue\\s+invoices?\\b", "\\boverdue\\s+amount\\b");
        put(map, "CREDIT_LIMIT", "credit limit", 50, "\\bcredit\\s+limit\\b", "\\bcreditlimit\\b");
        put(map, "PAYMENT", "payment terms", 40,
                "\\bpayment information\\b",
                "\\bpayment info\\b",
                "\\bpayment\\b");
        put(map, "INSURANCE", "insurance", 40, "\\binsurance\\b", "\\bcredit\\s+insurance\\b");
        put(map, "CURRENCY", "currency", 40, "\\bcurrency\\b");
        put(map, "VAT", "VAT", 40, "\\bvat\\b", "\\btax\\s+code\\b", "\\bvat\\s+code\\b");
        put(map, "PAYER", "payer", 10, "\\bpayer\\b");
        put(map, "EMAIL", "email", 30, "\\be-?mail\\b", "\\bmail\\b");
        put(map, "SALESPERSON", "salesperson", 30, "\\bsalesperson\\b", "\\bsales rep\\b", "\\bhandled by\\b");
        put(map, "DELIVERY_DATE", "delivery date", 30, "\\bdelivery date\\b", "\\bdelivery\\s+date\\b");
        put(map, "ORDER_AMOUNT", "order amount", 20, "\\border amount\\b", "\\bamount\\b");
        put(map, "ORDER_STATUS", "order status", 50, "\\border status\\b");
        put(map, "ORDER_NUMBER", "order number", 30, "\\border number\\b", "\\border no\\b");
        put(map, "LOYALTY_TIER", "loyalty tier", 30, "\\bloyalty\\b", "\\bloyalty tier\\b");
        put(map, "NET_ORDER_VALUE", "net order value", 45,
                "\\bnet order value\\b", "\\bnet order amount\\b", "\\bnet value\\b");
        put(map, "ORDER_CURRENCY", "order currency", 40, "\\border currency\\b");
        put(map, "DELIVERY_METHOD", "delivery method", 40,
                "\\bdelivery method\\b", "\\bdelivery mode\\b");
        put(map, "DELIVERY_TERMS", "delivery terms", 35, "\\bdelivery terms\\b");
        put(map, "ORDER_BLOCK_CODE", "order block", 35, "\\border block\\b", "\\bblock code\\b");
        put(map, "TIME_ZONE", "time zone", 30, "\\btime zone\\b", "\\btimezone\\b");
        put(map, "FREIGHT", "freight", 30, "\\bfreight\\b");
        put(map, "DELIVERY_MODEL", "delivery model", 30, "\\bdelivery model\\b");
        put(map, "ADDRESS_ID", "address id", 25, "\\baddress id\\b");
        put(map, "CUSTOMER", "customer number", 25,
                "\\bcustomer number\\b", "\\bcustomer no\\b", "\\bcustomer id\\b");
        put(map, "ORDER_DATE", "order date", 35, "\\border date\\b");
        put(map, "BUYER", "buyer", 40, "\\bbuyer\\b", "\\bpurchaser\\b");
        put(map, "SUPPLIER", "supplier", 40, "\\bsupplier\\b", "\\bvendor\\b");
        put(map, "WAREHOUSE", "warehouse", 35, "\\bwarehouse\\b");
        put(map, "PURCHASE_CATEGORY", "purchase category", 35,
                "\\bpurchase category\\b", "\\bpurchase cat\\b");
        put(map, "ORDER_TYPE", "order type", 30, "\\border type\\b");
        put(map, "DIVISION", "division", 30, "\\bdivision\\b");
        put(map, "PURCHASE_ORDER_NUMBER", "purchase order number", 35,
                "\\bpurchase order number\\b", "\\bpo number\\b");
        put(map, "MANUFACTURING_ORDER_NUMBER", "manufacturing order number", 35,
                "\\bmanufacturing order number\\b", "\\bmo number\\b");
        put(map, "PRODUCT_NAME", "product name", 35, "\\bproduct name\\b");
        put(map, "PRODUCT_NUMBER", "product number", 35, "\\bproduct number\\b");
        put(map, "REFERENCE_ORDER_NUMBER", "reference order number", 40,
                "\\breference order number\\b", "\\breference order\\b");
        put(map, "REFERENCE_ORDER_LINE", "reference order line", 30, "\\breference order line\\b");
        put(map, "PRIORITY", "priority", 30, "\\bpriority\\b");
        put(map, "PLANNED_START_DATE", "planned start date", 35,
                "\\bplanned start\\b", "\\bplanned start date\\b");
        put(map, "PLANNED_FINISH_DATE", "planned finish date", 35,
                "\\bplanned finish\\b", "\\bplanned finish date\\b");
        put(map, "DISTRIBUTION_ORDER_NUMBER", "distribution order number", 35,
                "\\bdistribution order number\\b");
        put(map, "RECEIVING_DATE", "receiving date", 35, "\\breceiving date\\b");
        put(map, "RESPONSIBLE", "responsible", 30, "\\bresponsible\\b", "\\bassigned to\\b");
        put(map, "HIGHEST_STATUS", "highest status", 40, "\\bhighest status\\b");
        put(map, "LOWEST_STATUS", "lowest status", 40, "\\blowest status\\b");
        put(map, "COUNTRY", "country", 30, "\\bcountry\\b");
        put(map, "CUSTOMER_TYPE", "customer type", 30, "\\bcustomer type\\b");
        put(map, "FAX", "fax", 25, "\\bfax\\b");
        put(map, "CITY", "city", 25, "\\bcity\\b");
        put(map, "POSTAL_CODE", "postal code", 25, "\\bpostal code\\b", "\\bzip code\\b");
        put(map, "CREDIT_LIMIT_2", "credit limit 2", 45, "\\bcredit limit 2\\b");
        put(map, "CREDIT_LIMIT_3", "credit limit 3", 45, "\\bcredit limit 3\\b");
        put(map, "OVERDUE_DUE", "overdue due", 40, "\\boverdue due\\b");
        put(map, "TOTAL_DUE_INVOICES", "total due invoices", 40, "\\btotal due invoices\\b");
        put(map, "TOTAL_OUTSTANDING_INVOICES", "total outstanding invoices", 40,
                "\\btotal outstanding invoices\\b");
        put(map, "INSURANCE_COMPANY", "insurance company", 35, "\\binsurance company\\b");
        put(map, "INSURANCE_NUMBER", "insurance number", 35, "\\binsurance number\\b");
        put(map, "INSURANCE_LIMIT", "insurance limit", 35, "\\binsurance limit\\b");
        put(map, "PAYMENT_CODE", "payment code", 30, "\\bpayment code\\b");
        put(map, "TERMS_CODE", "terms code", 30, "\\bterms code\\b");
        put(map, "TAX_CODE", "tax code", 30, "\\btax code\\b");
        put(map, "BLOCK_CODE", "block code", 30, "\\bblock code\\b");
        return map;
    }

    private static void put(
            Map<String, InformationRequestDefinition> map,
            String code,
            String displayName,
            int priority,
            String... regexes
    ) {
        List<Pattern> patterns = new ArrayList<>();
        for (String regex : regexes) {
            patterns.add(Pattern.compile(regex, Pattern.CASE_INSENSITIVE));
        }
        map.put(code, new InformationRequestDefinition(code, displayName, List.copyOf(patterns), priority));
    }
}
