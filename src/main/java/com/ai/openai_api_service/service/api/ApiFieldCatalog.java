package com.ai.openai_api_service.service.api;

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
 * Per-API metadata: which information codes map to which M3 return columns.
 */
@Component
public class ApiFieldCatalog {

    public record ApiEntry(String friendlyApiName, Map<String, List<String>> codeToColumns) {
        public ApiEntry {
            codeToColumns = codeToColumns != null ? Map.copyOf(codeToColumns) : Map.of();
        }
    }

    private final Map<M3ApiKey, ApiEntry> byApi;
    private final Map<String, Set<M3ApiKey>> apisByInformationCode;

    public ApiFieldCatalog() {
        Map<M3ApiKey, ApiEntry> seeded = new LinkedHashMap<>();
        seedGetBasicData(seeded);
        seedOisSearchHead(seeded);
        this.byApi = Map.copyOf(seeded);
        this.apisByInformationCode = Map.copyOf(buildReverseIndex(seeded));
    }

    public ApiEntry entryFor(M3ApiKey apiKey) {
        return apiKey == null ? null : byApi.get(apiKey);
    }

    public List<String> columnsFor(M3ApiKey apiKey, String informationCode) {
        if (apiKey == null || informationCode == null || informationCode.isBlank()) {
            return List.of();
        }
        ApiEntry entry = byApi.get(apiKey);
        if (entry == null) {
            return List.of();
        }
        String code = normalizeCode(informationCode);
        List<String> columns = entry.codeToColumns().get(code);
        if (columns == null) {
            columns = entry.codeToColumns().get(legacyAlias(code));
        }
        return columns != null ? List.copyOf(columns) : List.of();
    }

    public List<String> friendlyApiNamesForInformationCode(String informationCode) {
        if (informationCode == null || informationCode.isBlank()) {
            return List.of();
        }
        Set<M3ApiKey> apis = apisByInformationCode.get(normalizeCode(informationCode));
        if (apis == null || apis.isEmpty()) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (M3ApiKey key : apis) {
            ApiEntry entry = byApi.get(key);
            if (entry != null && !names.contains(entry.friendlyApiName())) {
                names.add(entry.friendlyApiName());
            }
        }
        return List.copyOf(names);
    }

    private static String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private static String legacyAlias(String code) {
        return switch (code) {
            case RequestedInformationResolver.BASIC -> "CUSTOMER_NAME";
            default -> code;
        };
    }

    private static Map<String, Set<M3ApiKey>> buildReverseIndex(Map<M3ApiKey, ApiEntry> seeded) {
        Map<String, Set<M3ApiKey>> reverse = new LinkedHashMap<>();
        for (Map.Entry<M3ApiKey, ApiEntry> e : seeded.entrySet()) {
            for (String code : e.getValue().codeToColumns().keySet()) {
                reverse.computeIfAbsent(code, k -> new LinkedHashSet<>()).add(e.getKey());
            }
        }
        Map<String, Set<M3ApiKey>> immutable = new LinkedHashMap<>();
        reverse.forEach((code, set) -> immutable.put(code, Set.copyOf(set)));
        return immutable;
    }

    private static void seedGetBasicData(Map<M3ApiKey, ApiEntry> seeded) {
        Map<String, List<String>> fields = new LinkedHashMap<>();
        fields.put(RequestedInformationResolver.PHONE, List.of("PHNO"));
        fields.put(RequestedInformationResolver.EMAIL, List.of("MAIL"));
        fields.put(RequestedInformationResolver.ADDRESS, List.of("CUA1", "CUA2", "CUA3", "CUA4", "TOWN", "PONO"));
        fields.put(RequestedInformationResolver.STATUS, List.of("STAT"));
        fields.put("CUSTOMER_NAME", List.of("CUNM"));
        fields.put(RequestedInformationResolver.BASIC, List.of("CUNM", "CUNO"));
        fields.put("CITY", List.of("TOWN"));
        fields.put("POSTAL_CODE", List.of("PONO"));
        fields.put("CURRENCY", List.of("CUCD"));
        seeded.put(
                M3ApiKey.of("CRS610MI", "GetBasicData"),
                new ApiEntry("Customer Basic Data", Map.copyOf(fields))
        );
    }

    private static void seedOisSearchHead(Map<M3ApiKey, ApiEntry> seeded) {
        Map<String, List<String>> fields = new LinkedHashMap<>();
        fields.put("ORDER_NUMBER", List.of("ORNO"));
        fields.put("ORDER_STATUS", List.of("ORNO", "ORST"));
        fields.put(RequestedInformationResolver.STATUS, List.of("ORNO", "ORST"));
        fields.put("SALESPERSON", List.of("ORNO", "SMCD"));
        fields.put("DELIVERY_DATE", List.of("ORNO", "RLDZ"));
        fields.put("ORDER_AMOUNT", List.of("ORNO", "NTAM"));
        fields.put("PAYMENT_TERMS", List.of("ORNO", "TEPY"));
        fields.put("ORDER_DATE", List.of("ORNO", "ORDT"));
        fields.put("FACILITY", List.of("ORNO", "FACI"));
        fields.put("CUSTOMER", List.of("ORNO", "CUNO"));
        seeded.put(
                M3ApiKey.of("OIS100MI", "SearchHead"),
                new ApiEntry("Customer Order Search", Map.copyOf(fields))
        );
    }
}
