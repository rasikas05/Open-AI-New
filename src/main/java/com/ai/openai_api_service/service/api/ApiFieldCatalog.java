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

    public record ApiEntry(String friendlyApiName, Map<String, List<ApiField>> codeToFields) {
        public ApiEntry {
            codeToFields = codeToFields != null ? copyCodeToFields(codeToFields) : Map.of();
        }

        private static Map<String, List<ApiField>> copyCodeToFields(Map<String, List<ApiField>> source) {
            Map<String, List<ApiField>> copy = new LinkedHashMap<>();
            source.forEach((code, fields) -> copy.put(code, List.copyOf(fields)));
            return Map.copyOf(copy);
        }

        Map<String, List<String>> codeToColumns() {
            Map<String, List<String>> columns = new LinkedHashMap<>();
            codeToFields.forEach((code, fields) ->
                    columns.put(code, fields.stream().map(ApiField::field).toList()));
            return columns;
        }
    }

    private final Map<M3ApiKey, ApiEntry> byApi;
    private final Map<String, Set<M3ApiKey>> apisByInformationCode;

    public ApiFieldCatalog() {
        Map<M3ApiKey, ApiEntry> seeded = new LinkedHashMap<>();
        seedGetBasicData(seeded);
        seedGetFinancial(seeded);
        seedOisSearchHead(seeded);
        seedPpsSearchHead(seeded);
        seedPmsSearchMo(seeded);
        seedMmsSearchHead(seeded);
        this.byApi = Map.copyOf(seeded);
        this.apisByInformationCode = Map.copyOf(buildReverseIndex(seeded));
    }

    public ApiEntry entryFor(M3ApiKey apiKey) {
        return apiKey == null ? null : byApi.get(apiKey);
    }

    public Map<String, List<String>> columnsByInformationCode(M3ApiKey apiKey) {
        ApiEntry entry = entryFor(apiKey);
        return entry != null ? entry.codeToColumns() : Map.of();
    }

    public List<ApiField> fieldsFor(M3ApiKey apiKey, String informationCode) {
        if (apiKey == null || informationCode == null || informationCode.isBlank()) {
            return List.of();
        }
        ApiEntry entry = byApi.get(apiKey);
        if (entry == null) {
            return List.of();
        }
        String code = normalizeCode(informationCode);
        List<ApiField> fields = entry.codeToFields().get(code);
        if (fields == null) {
            fields = entry.codeToFields().get(legacyAlias(code));
        }
        return fields != null ? List.copyOf(fields) : List.of();
    }

    public List<String> columnsFor(M3ApiKey apiKey, String informationCode) {
        return fieldsFor(apiKey, informationCode).stream()
                .map(ApiField::field)
                .toList();
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
            for (String code : e.getValue().codeToFields().keySet()) {
                reverse.computeIfAbsent(code, k -> new LinkedHashSet<>()).add(e.getKey());
            }
        }
        Map<String, Set<M3ApiKey>> immutable = new LinkedHashMap<>();
        reverse.forEach((code, set) -> immutable.put(code, Set.copyOf(set)));
        return immutable;
    }

    private static void seedGetBasicData(Map<M3ApiKey, ApiEntry> seeded) {
        Map<String, List<ApiField>> fields = new LinkedHashMap<>();
        fields.put(RequestedInformationResolver.PHONE, List.of(ApiField.of("PHNO", "Phone")));
        fields.put(RequestedInformationResolver.EMAIL, List.of(ApiField.of("MAIL", "Email")));
        fields.put(RequestedInformationResolver.ADDRESS, List.of(
                ApiField.of("CUA1", "Address line 1"),
                ApiField.of("CUA2", "Address line 2"),
                ApiField.of("CUA3", "Address line 3"),
                ApiField.of("CUA4", "Address line 4"),
                ApiField.of("TOWN", "City"),
                ApiField.of("PONO", "Postal code")
        ));
        fields.put(RequestedInformationResolver.STATUS, List.of(ApiField.of("STAT", "Status", ApiFieldMetadata.STATUS)));
        fields.put("CUSTOMER_NAME", List.of(ApiField.of("CUNM", "Customer name")));
        fields.put(RequestedInformationResolver.BASIC, List.of(
                ApiField.of("CUNM", "Customer name"),
                ApiField.of("CUNO", "Customer number")
        ));
        fields.put("CITY", List.of(ApiField.of("TOWN", "City")));
        fields.put("POSTAL_CODE", List.of(ApiField.of("PONO", "Postal code")));
        fields.put(RequestedInformationResolver.CURRENCY, List.of(
                ApiField.of("CUCD", "Currency", ApiFieldMetadata.CODE)
        ));
        fields.put("COUNTRY", List.of(ApiField.of("CSCD", "Country", ApiFieldMetadata.CODE)));
        fields.put("CUSTOMER_TYPE", List.of(ApiField.of("CUTP", "Customer type", ApiFieldMetadata.CODE)));
        fields.put("FAX", List.of(ApiField.of("TFNO", "Fax")));
        seeded.put(
                M3ApiKey.of("CRS610MI", "GetBasicData"),
                new ApiEntry("Customer Basic Data", Map.copyOf(fields))
        );
    }

    /**
     * CRS610MI/GetFinancial — live MI response is source of truth for column IDs.
     * Obsolete columns (ACLS, TEPY, PYNO, CRIN, OBAM, ODAM, ADDRESS, PHONE, EMAIL) are
     * omitted here only; OIS and other APIs may still use PYNO/TEPY.
     */
    private static void seedGetFinancial(Map<M3ApiKey, ApiEntry> seeded) {
        Map<String, List<ApiField>> fields = new LinkedHashMap<>();
        fields.put(RequestedInformationResolver.BASIC, List.of(
                ApiField.of("CUNO", "Customer number"),
                ApiField.of("CRLM", "Credit limit", ApiFieldMetadata.AMOUNT)
        ));
        fields.put(RequestedInformationResolver.CREDIT_LIMIT, List.of(
                ApiField.of("CRLM", "Credit limit", ApiFieldMetadata.AMOUNT)
        ));
        fields.put(RequestedInformationResolver.PAYMENT, List.of(
                ApiField.of("PYCD", "Payment method AR", ApiFieldMetadata.CODE)
        ));
        fields.put("PAYMENT_TERMS", List.of(
                ApiField.of("TECD", "Cash discount term", ApiFieldMetadata.CODE)
        ));
        fields.put(RequestedInformationResolver.CURRENCY, List.of(
                ApiField.of("CUCD", "Currency", ApiFieldMetadata.CODE)
        ));
        fields.put(RequestedInformationResolver.VAT, List.of(
                ApiField.of("VTCD", "VAT code", ApiFieldMetadata.CODE)
        ));
        fields.put(RequestedInformationResolver.GROUP_PAYER, List.of(
                ApiField.of("PYGR", "Group payer")
        ));
        fields.put(RequestedInformationResolver.INVOICE_RECIPIENT, List.of(
                ApiField.of("INRC", "Invoice recipient")
        ));
        fields.put(RequestedInformationResolver.INSURANCE, List.of(
                ApiField.of("INCO", "Insurance company"),
                ApiField.of("INSN", "Insurance number"),
                ApiField.of("INLI", "Insurance limit", ApiFieldMetadata.AMOUNT)
        ));
        fields.put(RequestedInformationResolver.OUTSTANDING_INVOICES, List.of(
                ApiField.of("TOIN", "Outstanding invoice amount", ApiFieldMetadata.AMOUNT)
        ));
        fields.put(RequestedInformationResolver.OVERDUE_INVOICES, List.of(
                ApiField.of("TDIN", "Overdue invoice amount", ApiFieldMetadata.AMOUNT)
        ));
        fields.put("CREDIT_LIMIT_2", List.of(ApiField.of("CRL2", "Credit limit 2", ApiFieldMetadata.AMOUNT)));
        fields.put("CREDIT_LIMIT_3", List.of(ApiField.of("CRL3", "Credit limit 3", ApiFieldMetadata.AMOUNT)));
        fields.put("OVERDUE_DUE", List.of(ApiField.of("ODUD", "Overdue due", ApiFieldMetadata.AMOUNT)));
        fields.put("TOTAL_DUE_INVOICES", List.of(ApiField.of("TDIN", "Total due invoices", ApiFieldMetadata.AMOUNT)));
        fields.put("TOTAL_OUTSTANDING_INVOICES", List.of(ApiField.of("TOIN", "Total outstanding invoices", ApiFieldMetadata.AMOUNT)));
        fields.put("INSURANCE_COMPANY", List.of(ApiField.of("INCO", "Insurance company")));
        fields.put("INSURANCE_NUMBER", List.of(ApiField.of("INSN", "Insurance number")));
        fields.put("INSURANCE_LIMIT", List.of(ApiField.of("INLI", "Insurance limit", ApiFieldMetadata.AMOUNT)));
        fields.put("PAYMENT_CODE", List.of(ApiField.of("PYCD", "Payment code", ApiFieldMetadata.CODE)));
        fields.put("TERMS_CODE", List.of(ApiField.of("TECD", "Terms code", ApiFieldMetadata.CODE)));
        fields.put("TAX_CODE", List.of(ApiField.of("TAXC", "Tax code", ApiFieldMetadata.CODE)));
        fields.put("BLOCK_CODE", List.of(ApiField.of("BLCD", "Block code", ApiFieldMetadata.CODE)));
        seeded.put(
                M3ApiKey.of("CRS610MI", "GetFinancial"),
                new ApiEntry("Customer Financial", Map.copyOf(fields))
        );
    }

    private static void seedOisSearchHead(Map<M3ApiKey, ApiEntry> seeded) {
        Map<String, List<ApiField>> fields = new LinkedHashMap<>();
        fields.put("ORDER_NUMBER", List.of(ApiField.of("ORNO", "Order number")));
        fields.put("ORDER_STATUS", List.of(
                ApiField.of("ORNO", "Order number"),
                ApiField.of("ORST", "Order status", ApiFieldMetadata.STATUS)
        ));
        fields.put(RequestedInformationResolver.STATUS, List.of(
                ApiField.of("ORNO", "Order number"),
                ApiField.of("ORST", "Order status", ApiFieldMetadata.STATUS)
        ));
        fields.put("SALESPERSON", List.of(
                ApiField.of("ORNO", "Order number"),
                ApiField.of("SMCD", "Salesperson")
        ));
        fields.put("DELIVERY_DATE", List.of(
                ApiField.of("ORNO", "Order number"),
                ApiField.of("RLDZ", "Delivery date", ApiFieldMetadata.DATE)
        ));
        fields.put("ORDER_AMOUNT", List.of(
                ApiField.of("ORNO", "Order number"),
                ApiField.of("NTAM", "Order amount", ApiFieldMetadata.AMOUNT)
        ));
        fields.put("PAYMENT_TERMS", List.of(
                ApiField.of("ORNO", "Order number"),
                ApiField.of("TEPY", "Payment terms", ApiFieldMetadata.CODE)
        ));
        fields.put("ORDER_DATE", List.of(
                ApiField.of("ORNO", "Order number"),
                ApiField.of("ORDT", "Order date", ApiFieldMetadata.DATE)
        ));
        fields.put("FACILITY", List.of(
                ApiField.of("ORNO", "Order number"),
                ApiField.of("FACI", "Facility", ApiFieldMetadata.CODE)
        ));
        fields.put("CUSTOMER", List.of(
                ApiField.of("ORNO", "Order number"),
                ApiField.of("CUNO", "Customer number")
        ));
        fields.put("NET_ORDER_VALUE", List.of(
                ApiField.of("ORNO", "Order number"),
                ApiField.of("NTAM", "Net order value", ApiFieldMetadata.AMOUNT)
        ));
        fields.put("ORDER_CURRENCY", List.of(
                ApiField.of("ORNO", "Order number"),
                ApiField.of("CUCD", "Order currency", ApiFieldMetadata.CODE)
        ));
        fields.put("DELIVERY_METHOD", List.of(
                ApiField.of("ORNO", "Order number"),
                ApiField.of("MODL", "Delivery method", ApiFieldMetadata.CODE)
        ));
        fields.put("DELIVERY_TERMS", List.of(
                ApiField.of("ORNO", "Order number"),
                ApiField.of("TEDL", "Delivery terms", ApiFieldMetadata.CODE)
        ));
        fields.put("ADDRESS_ID", List.of(
                ApiField.of("ORNO", "Order number"),
                ApiField.of("ADID", "Address ID")
        ));
        fields.put("ORDER_BLOCK_CODE", List.of(
                ApiField.of("ORNO", "Order number"),
                ApiField.of("OBLC", "Order block code", ApiFieldMetadata.CODE)
        ));
        fields.put("TIME_ZONE", List.of(
                ApiField.of("ORNO", "Order number"),
                ApiField.of("TIZO", "Time zone", ApiFieldMetadata.CODE)
        ));
        fields.put("FREIGHT", List.of(
                ApiField.of("ORNO", "Order number"),
                ApiField.of("FRE1", "Freight", ApiFieldMetadata.CODE)
        ));
        fields.put("DELIVERY_MODEL", List.of(
                ApiField.of("ORNO", "Order number"),
                ApiField.of("MODL", "Delivery model", ApiFieldMetadata.CODE)
        ));
        fields.put("PAYER", List.of(
                ApiField.of("ORNO", "Order number"),
                ApiField.of("PYNO", "Payer")
        ));
        fields.put("ORDER_TYPE", List.of(
                ApiField.of("ORNO", "Order number"),
                ApiField.of("ORTP", "Order type", ApiFieldMetadata.CODE)
        ));
        fields.put("RESPONSIBLE", List.of(
                ApiField.of("ORNO", "Order number"),
                ApiField.of("RESP", "Responsible")
        ));
        fields.put("LOWEST_STATUS", List.of(
                ApiField.of("ORNO", "Order number"),
                ApiField.of("ORSL", "Lowest status", ApiFieldMetadata.STATUS)
        ));
        seeded.put(
                M3ApiKey.of("OIS100MI", "SearchHead"),
                new ApiEntry("Customer Order Search", Map.copyOf(fields))
        );
    }

    private static void seedPpsSearchHead(Map<M3ApiKey, ApiEntry> seeded) {
        Map<String, List<ApiField>> fields = new LinkedHashMap<>();
        fields.put("PURCHASE_ORDER_NUMBER", List.of(ApiField.of("PUNO", "Purchase order number")));
        fields.put(RequestedInformationResolver.STATUS, List.of(
                ApiField.of("PUNO", "Purchase order number"),
                ApiField.of("PUST", "Highest status", ApiFieldMetadata.STATUS)
        ));
        fields.put("BUYER", List.of(
                ApiField.of("PUNO", "Purchase order number"),
                ApiField.of("BUYE", "Buyer")
        ));
        fields.put("SUPPLIER", List.of(
                ApiField.of("PUNO", "Purchase order number"),
                ApiField.of("SUNO", "Supplier")
        ));
        fields.put("WAREHOUSE", List.of(
                ApiField.of("PUNO", "Purchase order number"),
                ApiField.of("WHLO", "Warehouse", ApiFieldMetadata.CODE)
        ));
        fields.put("FACILITY", List.of(
                ApiField.of("PUNO", "Purchase order number"),
                ApiField.of("FACI", "Facility", ApiFieldMetadata.CODE)
        ));
        fields.put("PURCHASE_CATEGORY", List.of(
                ApiField.of("PUNO", "Purchase order number"),
                ApiField.of("POTC", "Purchase category", ApiFieldMetadata.CODE)
        ));
        fields.put("ORDER_DATE", List.of(
                ApiField.of("PUNO", "Purchase order number"),
                ApiField.of("PUDT", "Order date", ApiFieldMetadata.DATE)
        ));
        fields.put("ORDER_TYPE", List.of(
                ApiField.of("PUNO", "Purchase order number"),
                ApiField.of("ORTY", "Order type", ApiFieldMetadata.CODE)
        ));
        fields.put("DIVISION", List.of(
                ApiField.of("PUNO", "Purchase order number"),
                ApiField.of("DIVI", "Division", ApiFieldMetadata.CODE)
        ));
        fields.put("LOWEST_STATUS", List.of(
                ApiField.of("PUNO", "Purchase order number"),
                ApiField.of("PUSL", "Lowest status", ApiFieldMetadata.STATUS)
        ));
        fields.put("REQUISITION_BY", List.of(
                ApiField.of("PUNO", "Purchase order number"),
                ApiField.of("PURC", "Requisition by")
        ));
        seeded.put(
                M3ApiKey.of("PPS200MI", "SearchHead"),
                new ApiEntry("Purchase Order Search", Map.copyOf(fields))
        );
    }

    private static void seedPmsSearchMo(Map<M3ApiKey, ApiEntry> seeded) {
        Map<String, List<ApiField>> fields = new LinkedHashMap<>();
        fields.put("MANUFACTURING_ORDER_NUMBER", List.of(ApiField.of("MFNO", "Manufacturing order number")));
        fields.put(RequestedInformationResolver.STATUS, List.of(
                ApiField.of("MFNO", "Manufacturing order number"),
                ApiField.of("WHST", "Manufacturing status", ApiFieldMetadata.STATUS)
        ));
        fields.put("PRODUCT_NAME", List.of(
                ApiField.of("MFNO", "Manufacturing order number"),
                ApiField.of("ITDS", "Product name")
        ));
        fields.put("PRODUCT_NUMBER", List.of(
                ApiField.of("MFNO", "Manufacturing order number"),
                ApiField.of("PRNO", "Product number")
        ));
        fields.put("REFERENCE_ORDER_CATEGORY", List.of(
                ApiField.of("MFNO", "Manufacturing order number"),
                ApiField.of("RORC", "Reference order category", ApiFieldMetadata.CODE)
        ));
        fields.put("REFERENCE_ORDER_NUMBER", List.of(
                ApiField.of("MFNO", "Manufacturing order number"),
                ApiField.of("RORN", "Reference order number")
        ));
        fields.put("REFERENCE_ORDER_LINE", List.of(
                ApiField.of("MFNO", "Manufacturing order number"),
                ApiField.of("RORL", "Reference order line")
        ));
        fields.put("REFERENCE_ORDER_SUFFIX", List.of(
                ApiField.of("MFNO", "Manufacturing order number"),
                ApiField.of("RORX", "Reference order suffix")
        ));
        fields.put("ORDER_QUANTITY", List.of(
                ApiField.of("MFNO", "Manufacturing order number"),
                ApiField.of("ORQT", "Order quantity", ApiFieldMetadata.AMOUNT)
        ));
        fields.put("MANUFACTURED_QUANTITY", List.of(
                ApiField.of("MFNO", "Manufacturing order number"),
                ApiField.of("MAQT", "Manufactured quantity", ApiFieldMetadata.AMOUNT)
        ));
        fields.put("PRIORITY", List.of(
                ApiField.of("MFNO", "Manufacturing order number"),
                ApiField.of("PRIO", "Priority", ApiFieldMetadata.CODE)
        ));
        fields.put("PLANNED_START_DATE", List.of(
                ApiField.of("MFNO", "Manufacturing order number"),
                ApiField.of("STDT", "Planned start date", ApiFieldMetadata.DATE)
        ));
        fields.put("PLANNED_FINISH_DATE", List.of(
                ApiField.of("MFNO", "Manufacturing order number"),
                ApiField.of("FIDT", "Planned finish date", ApiFieldMetadata.DATE)
        ));
        fields.put("FACILITY", List.of(
                ApiField.of("MFNO", "Manufacturing order number"),
                ApiField.of("FACI", "Facility", ApiFieldMetadata.CODE)
        ));
        fields.put("PLANNED_START_TIME", List.of(
                ApiField.of("MFNO", "Manufacturing order number"),
                ApiField.of("MSTI", "Planned start time")
        ));
        fields.put("PLANNED_FINISH_TIME", List.of(
                ApiField.of("MFNO", "Manufacturing order number"),
                ApiField.of("MFTI", "Planned finish time")
        ));
        seeded.put(
                M3ApiKey.of("PMS100MI", "SearchMO"),
                new ApiEntry("Manufacturing Order Search", Map.copyOf(fields))
        );
    }

    private static void seedMmsSearchHead(Map<M3ApiKey, ApiEntry> seeded) {
        Map<String, List<ApiField>> fields = new LinkedHashMap<>();
        fields.put("DISTRIBUTION_ORDER_NUMBER", List.of(ApiField.of("TRNR", "Distribution order number")));
        fields.put(RequestedInformationResolver.STATUS, List.of(
                ApiField.of("TRNR", "Distribution order number"),
                ApiField.of("TRSH", "Highest status", ApiFieldMetadata.STATUS)
        ));
        fields.put("ORDER_TYPE", List.of(
                ApiField.of("TRNR", "Distribution order number"),
                ApiField.of("TRTP", "Order type", ApiFieldMetadata.CODE)
        ));
        fields.put("RESPONSIBLE", List.of(
                ApiField.of("TRNR", "Distribution order number"),
                ApiField.of("RESP", "Responsible")
        ));
        fields.put("RECEIVING_DATE", List.of(
                ApiField.of("TRNR", "Distribution order number"),
                ApiField.of("RIDT", "Receiving date", ApiFieldMetadata.DATE)
        ));
        fields.put("WAREHOUSE", List.of(
                ApiField.of("TRNR", "Distribution order number"),
                ApiField.of("WHLO", "Warehouse", ApiFieldMetadata.CODE)
        ));
        fields.put("FACILITY", List.of(
                ApiField.of("TRNR", "Distribution order number"),
                ApiField.of("FACI", "Facility", ApiFieldMetadata.CODE)
        ));
        fields.put("HIGHEST_STATUS", List.of(
                ApiField.of("TRNR", "Distribution order number"),
                ApiField.of("TRSH", "Highest status", ApiFieldMetadata.STATUS)
        ));
        fields.put("LOWEST_STATUS", List.of(
                ApiField.of("TRNR", "Distribution order number"),
                ApiField.of("TRSL", "Lowest status", ApiFieldMetadata.STATUS)
        ));
        seeded.put(
                M3ApiKey.of("MMS100MI", "SearchHead"),
                new ApiEntry("Distribution Order Search", Map.copyOf(fields))
        );
    }
}
