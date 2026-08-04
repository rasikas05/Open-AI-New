package com.ai.openai_api_service.service.protection;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Java-seeded classification + detection metadata (governance doc 01). Does not parse Markdown at runtime.
 *
 * <p>Detection lists are split for analytics:
 * <ul>
 *   <li>{@code detectionKeywords} — natural language users say</li>
 *   <li>{@code detectionAliases} — system / abbreviation forms</li>
 * </ul>
 * Do not mix abbreviations into the keyword list. Never add bare {@code account} or bare {@code material}.
 *
 * <p>Identifier rows use {@link ValueShapeValidator#M3_IDENTIFIER} with required {@code maxLength}
 * and {@link IdentifierCharacterSet}.
 */
@Component
public class FieldClassificationCatalog {

    private final Map<String, FieldClassification> byCode;

    public FieldClassificationCatalog() {
        this.byCode = Collections.unmodifiableMap(seed());
    }

    public Optional<FieldClassification> lookup(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(byCode.get(code.trim().toUpperCase(Locale.ROOT)));
    }

    /** All seeded rows — used by the metadata-driven detector. */
    public List<FieldClassification> all() {
        return List.copyOf(byCode.values());
    }

    private static Map<String, FieldClassification> seed() {
        Map<String, FieldClassification> map = new LinkedHashMap<>();
        putId(map, "CUNO", "Customer number", InformationCategory.BDI, LlmExposurePolicy.REPLACE,
                "Customer Number", "High", "Identifies a customer",
                List.of(
                        "customer", "customer number", "customer id",
                        "client", "client number", "client id",
                        "account number", "account id",
                        "customer code", "customer reference", "customer ref"
                ),
                List.of("cuno", "customer no", "cust", "cust no", "cust#", "customer#"),
                10);
        putId(map, "ORNO", "Customer order number", InformationCategory.BDI, LlmExposurePolicy.REPLACE,
                "Order Number", "High", "Identifies a customer order",
                List.of(
                        "order", "order number", "customer order", "customer order number",
                        "sales order", "sales order number", "customer order no"
                ),
                List.of("orno", "order no", "order#", "so number", "so#"),
                10);
        putId(map, "PUNO", "Purchase order number", InformationCategory.BDI, LlmExposurePolicy.REPLACE,
                "Purchase Order Number", "High", "Identifies a purchase order",
                List.of("purchase order", "po number", "purchase order number", "purchase order no"),
                List.of("puno", "po", "po#", "po no", "po id"),
                10);
        putId(map, "MFNO", "Manufacturing order number", InformationCategory.BDI, LlmExposurePolicy.REPLACE,
                "Manufacturing Order Number", "High", "Identifies a manufacturing order",
                List.of(
                        "manufacturing order", "mo number", "manufacturing order number",
                        "manufacturing order no", "mfg order"
                ),
                List.of("mfno", "mo#"),
                10);
        putId(map, "TRNR", "Distribution order number", InformationCategory.BDI, LlmExposurePolicy.REPLACE,
                "Distribution Order Number", "High", "Identifies a distribution order",
                List.of(
                        "distribution order", "do number", "transfer order",
                        "distribution order number", "transfer order number"
                ),
                List.of("trnr", "do#"),
                10);
        putId(map, "SUNO", "Supplier", InformationCategory.BDI, LlmExposurePolicy.REPLACE,
                "Supplier Number", "High", "Identifies a supplier",
                List.of(
                        "supplier", "supplier number", "vendor", "vendor number",
                        "supplier code"
                ),
                List.of("suno", "supplier no", "vendor no", "supplier id", "vendor id", "vendor code"),
                10);
        putId(map, "PRNO", "Product number", InformationCategory.BDI, LlmExposurePolicy.REPLACE,
                "Product Number", "High", "Identifies a product",
                List.of(
                        "product", "product number", "item number", "item",
                        "product code", "item code", "material number", "material code"
                ),
                List.of("prno", "product no", "item no", "sku"),
                15);
        put(map, "WHLO", "Warehouse", InformationCategory.OMD, LlmExposurePolicy.ALLOW,
                null, "High", "Operational context only",
                List.of("warehouse", "warehouse code", "warehouse id"),
                List.of("whlo", "whs"),
                ValueShapeValidator.M3_SITE_CODE, null, null);
        put(map, "FACI", "Facility", InformationCategory.OMD, LlmExposurePolicy.ALLOW,
                null, "High", "Operational context only",
                List.of("facility", "plant", "facility code", "plant code"),
                List.of("faci"),
                ValueShapeValidator.M3_SITE_CODE, null, null);
        put(map, "DIVI", "Division", InformationCategory.OMD, LlmExposurePolicy.ALLOW,
                null, "High", "Operational context only",
                List.of("division", "division code"),
                List.of("divi"),
                ValueShapeValidator.M3_SITE_CODE, null, null);
        put(map, "MAIL", "E-mail address", InformationCategory.PII, LlmExposurePolicy.REPLACE,
                "Email", "High", "Personally identifiable",
                List.of("email", "e-mail", "mail"),
                List.of(),
                ValueShapeValidator.GENERIC_TOKEN, null, null);
        put(map, "PHNO", "Telephone number", InformationCategory.PII, LlmExposurePolicy.REPLACE,
                "Phone", "High", "Personally identifiable",
                List.of("phone", "telephone", "phone number"),
                List.of("phno"),
                ValueShapeValidator.GENERIC_TOKEN, null, null);
        put(map, "RESP", "Responsible", InformationCategory.PII, LlmExposurePolicy.REPLACE,
                "Employee Id", "High", "Employee identifier",
                List.of("responsible", "assigned to", "owner"),
                List.of("resp"),
                ValueShapeValidator.M3_PERSON_CODE, null, null);
        put(map, "SMCD", "Salesperson", InformationCategory.PII, LlmExposurePolicy.REPLACE,
                "Employee Id", "High", "Employee identifier",
                List.of("salesperson", "sales person", "sales representative", "sales rep"),
                List.of("smcd", "salesman"),
                ValueShapeValidator.M3_PERSON_CODE, null, null);
        put(map, "BUYE", "Buyer", InformationCategory.PII, LlmExposurePolicy.REPLACE,
                "Employee Id", "High", "Employee identifier",
                List.of("buyer"),
                List.of("buye"),
                ValueShapeValidator.M3_PERSON_CODE, null, null);
        put(map, "CRLM", "Credit limit", InformationCategory.BFI, LlmExposurePolicy.BLOCK,
                "Credit Limit", "High", "Commercial financial exposure",
                List.of("credit limit"),
                List.of("crlm"),
                ValueShapeValidator.GENERIC_TOKEN, null, null);
        put(map, "NTAM", "Net order value", InformationCategory.BFI, LlmExposurePolicy.BLOCK,
                "Order Value", "High", "Commercial order value",
                List.of("net order value", "order value", "order amount"),
                List.of("ntam"),
                ValueShapeValidator.GENERIC_TOKEN, null, null);
        put(map, "KSTR", "Key string", InformationCategory.TECH, LlmExposurePolicy.BLOCK,
                "Technical Payload", "High", "May embed business identifiers",
                List.of("key string"),
                List.of("kstr"),
                ValueShapeValidator.GENERIC_TOKEN, null, null);
        return map;
    }

    private static void putId(
            Map<String, FieldClassification> map,
            String code,
            String meaning,
            InformationCategory category,
            LlmExposurePolicy policy,
            String placeholderType,
            String confidence,
            String reason,
            List<String> keywords,
            List<String> aliases,
            int maxLength
    ) {
        put(map, code, meaning, category, policy, placeholderType, confidence, reason,
                keywords, aliases,
                ValueShapeValidator.M3_IDENTIFIER,
                maxLength,
                IdentifierCharacterSet.ALPHANUMERIC);
    }

    private static void put(
            Map<String, FieldClassification> map,
            String code,
            String meaning,
            InformationCategory category,
            LlmExposurePolicy policy,
            String placeholderType,
            String confidence,
            String reason,
            List<String> keywords,
            List<String> aliases,
            String valueShapeKey,
            Integer maxLength,
            IdentifierCharacterSet characterSet
    ) {
        map.put(code, new FieldClassification(
                code, meaning, category, policy, placeholderType, confidence, reason,
                keywords, aliases, valueShapeKey, maxLength, characterSet
        ));
    }
}
