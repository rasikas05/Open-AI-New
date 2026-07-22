package com.ai.openai_api_service.service.query;

import com.ai.openai_api_service.service.IntentApiCatalog;
import com.ai.openai_api_service.service.RequestedInformationResolver;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Maps abstract requested-information groups to M3 return column lists per intent (metadata-driven).
 */
@Component
public class ReturnColumnCatalog {

    private final Map<String, Map<String, List<String>>> columnsByIntentAndGroup;

    public ReturnColumnCatalog(IntentApiCatalog intentApiCatalog) {
        this.columnsByIntentAndGroup = Map.copyOf(seed(intentApiCatalog));
    }

    /**
     * @return distinct M3 field names for MI {@code returncols}, or empty when FULL / unknown.
     */
    public List<String> columnsFor(String intentName, List<String> requestedInformation) {
        if (intentName == null || intentName.isBlank()) {
            return List.of();
        }
        if (requestedInformation == null || requestedInformation.isEmpty()) {
            return List.of();
        }
        if (requestedInformation.size() == 1
                && RequestedInformationResolver.FULL.equals(requestedInformation.getFirst())) {
            return List.of();
        }

        Map<String, List<String>> byGroup = columnsByIntentAndGroup.get(intentName);
        if (byGroup == null || byGroup.isEmpty()) {
            return List.of();
        }

        Set<String> columns = new LinkedHashSet<>();
        for (String group : requestedInformation) {
            if (group == null || group.isBlank() || RequestedInformationResolver.FULL.equals(group)) {
                continue;
            }
            List<String> mapped = byGroup.get(group.toUpperCase(Locale.ROOT));
            if (mapped != null) {
                columns.addAll(mapped);
            }
        }
        return List.copyOf(columns);
    }

    private static Map<String, Map<String, List<String>>> seed(IntentApiCatalog intentApiCatalog) {
        Map<String, Map<String, List<String>>> map = new LinkedHashMap<>();

        if (intentApiCatalog.contains("GetCustomer")) {
            map.put("GetCustomer", customerReadGroups());
        }
        if (intentApiCatalog.contains("GetCustomerFinancial")) {
            map.put("GetCustomerFinancial", customerFinancialGroups());
        }

        Map<String, List<String>> searchStatusOnly = Map.of(
                RequestedInformationResolver.STATUS, List.of("ORST", "PUST", "STAT")
        );
        for (String searchIntent : List.of(
                "SearchCustomerOrder",
                "SearchPurchaseOrder",
                "SearchManufacturingOrder",
                "SearchDistributionOrder"
        )) {
            if (intentApiCatalog.contains(searchIntent)) {
                map.put(searchIntent, searchStatusOnly);
            }
        }

        return map;
    }

    private static Map<String, List<String>> customerReadGroups() {
        Map<String, List<String>> groups = new LinkedHashMap<>();
        groups.put(RequestedInformationResolver.PHONE, List.of("PHNO"));
        groups.put(RequestedInformationResolver.EMAIL, List.of("EMAL"));
        groups.put(RequestedInformationResolver.ADDRESS, List.of("CUA1", "CUA2", "CUA3", "CUA4", "TOWN", "PONO"));
        groups.put(RequestedInformationResolver.STATUS, List.of("STAT"));
        groups.put(RequestedInformationResolver.BASIC, List.of("CUNM", "CUNO"));
        return Map.copyOf(groups);
    }

    private static Map<String, List<String>> customerFinancialGroups() {
        Map<String, List<String>> groups = new LinkedHashMap<>(customerReadGroups());
        groups.put(RequestedInformationResolver.BASIC, List.of("CUNO", "ACLS", "CRLM"));
        return Map.copyOf(groups);
    }
}
