package com.ai.openai_api_service.service.query;

import com.ai.openai_api_service.service.IntentApiCatalog;
import com.ai.openai_api_service.service.RequestedInformationResolver;
import com.ai.openai_api_service.service.api.ApiFieldCatalog;
import com.ai.openai_api_service.service.api.M3ApiKey;
import org.springframework.stereotype.Component;

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

    public ReturnColumnCatalog(IntentApiCatalog intentApiCatalog, ApiFieldCatalog apiFieldCatalog) {
        this.columnsByIntentAndGroup = Map.copyOf(seed(intentApiCatalog, apiFieldCatalog));
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

    private static Map<String, Map<String, List<String>>> seed(
            IntentApiCatalog intentApiCatalog,
            ApiFieldCatalog apiFieldCatalog
    ) {
        Map<String, Map<String, List<String>>> map = new LinkedHashMap<>();

        bindIntent(map, intentApiCatalog, apiFieldCatalog, "GetCustomer", "CRS610MI", "GetBasicData");
        bindIntent(map, intentApiCatalog, apiFieldCatalog, "GetCustomerFinancial", "CRS610MI", "GetFinancial");
        bindIntent(map, intentApiCatalog, apiFieldCatalog, "SearchCustomerOrder", "OIS100MI", "SearchHead");
        bindIntent(map, intentApiCatalog, apiFieldCatalog, "SearchPurchaseOrder", "PPS200MI", "SearchHead");
        bindIntent(map, intentApiCatalog, apiFieldCatalog, "SearchManufacturingOrder", "PMS100MI", "SearchMO");
        bindIntent(map, intentApiCatalog, apiFieldCatalog, "SearchDistributionOrder", "MMS100MI", "SearchHead");

        return map;
    }

    private static void bindIntent(
            Map<String, Map<String, List<String>>> map,
            IntentApiCatalog intentApiCatalog,
            ApiFieldCatalog apiFieldCatalog,
            String intentName,
            String program,
            String transaction
    ) {
        if (!intentApiCatalog.contains(intentName)) {
            return;
        }
        Map<String, List<String>> columns = apiFieldCatalog.columnsByInformationCode(
                M3ApiKey.of(program, transaction)
        );
        if (!columns.isEmpty()) {
            map.put(intentName, columns);
        }
    }
}
