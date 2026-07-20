package com.ai.openai_api_service.service;

import com.ai.openai_api_service.model.SearchFieldDefinition;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class SearchFieldCatalog {

    private final Map<String, List<SearchFieldDefinition>> fieldsByIntent;

    public SearchFieldCatalog() {
        Map<String, List<SearchFieldDefinition>> fields = new LinkedHashMap<>();
        seedSearchCustomerOrder(fields);
        seedSearchPurchaseOrder(fields);
        seedSearchManufacturingOrder(fields);
        seedSearchDistributionOrder(fields);
        seedGetCustomer(fields);
        seedGetCustomerFinancial(fields);

        Map<String, List<SearchFieldDefinition>> immutable = new LinkedHashMap<>();
        fields.forEach((intent, definitions) -> immutable.put(intent, List.copyOf(definitions)));
        this.fieldsByIntent = Map.copyOf(immutable);
    }

    public List<SearchFieldDefinition> fieldsFor(String intentName) {
        if (intentName == null || intentName.isBlank()) {
            return List.of();
        }
        return fieldsByIntent.getOrDefault(intentName, List.of());
    }

    public Optional<SearchFieldDefinition> find(String intentName, String m3Field) {
        if (m3Field == null || m3Field.isBlank()) {
            return Optional.empty();
        }
        return fieldsFor(intentName).stream()
                .filter(definition -> m3Field.equals(definition.m3Field()))
                .findFirst();
    }

    public Optional<SearchFieldDefinition> findBySlot(String intentName, String slotName) {
        if (slotName == null || slotName.isBlank()) {
            return Optional.empty();
        }
        return fieldsFor(intentName).stream()
                .filter(definition -> slotName.equals(definition.lexSlotName()))
                .findFirst();
    }

    public boolean contains(String intentName, String m3Field) {
        return find(intentName, m3Field).isPresent();
    }

    private static void seedSearchCustomerOrder(Map<String, List<SearchFieldDefinition>> fields) {
        String intent = "SearchCustomerOrder";
        add(fields, intent, "ORNO",
                List.of("order", "customer order", "customer order number", "order number"),
                "Customer Order Number",
                "CustomerOrderNumber");
        add(fields, intent, "CUNO",
                List.of("customer", "customer number", "customer id"),
                "Customer Number",
                "CustomerNumber");
        add(fields, intent, "FACI",
                List.of("facility", "plant"),
                "Facility",
                "Facility");
        add(fields, intent, "SMCD",
                List.of("salesperson", "sales representative", "handled by"),
                "Salesperson",
                "Salesperson");
        add(fields, intent, "RESP",
                List.of("responsible", "assigned to", "owner"),
                "Responsible Person",
                "Responsible");
        add(fields, intent, "ORST",
                List.of("status", "order status"),
                "Highest Order Status",
                "Status");
        add(fields, intent, "ORSL",
                List.of("lowest status"),
                "Lowest Order Status",
                null);
        add(fields, intent, "ORDT",
                List.of("order date", "placed on", "date"),
                "Order Date",
                "OrderDate");
        add(fields, intent, "PYNO",
                List.of("payer"),
                "Payer",
                null);
    }

    private static void seedSearchPurchaseOrder(Map<String, List<SearchFieldDefinition>> fields) {
        String intent = "SearchPurchaseOrder";
        add(fields, intent, "PUNO",
                List.of("purchase order", "purchase order number", "po number"),
                "Purchase Order Number",
                "PurchaseOrderNumber");
        add(fields, intent, "SUNO",
                List.of("supplier", "supplier number", "vendor"),
                "Supplier",
                "Supplier");
        add(fields, intent, "BUYE",
                List.of("buyer", "purchaser"),
                "Buyer",
                "Buyer");
        add(fields, intent, "WHLO",
                List.of("warehouse"),
                "Warehouse",
                "Warehouse");
        add(fields, intent, "FACI",
                List.of("facility", "plant"),
                "Facility",
                "Facility");
        add(fields, intent, "PUST",
                List.of("status", "purchase order status"),
                "Highest Purchase Order Status",
                "Status");
        add(fields, intent, "PUSL",
                List.of("lowest status"),
                "Lowest Purchase Order Status",
                null);
        add(fields, intent, "PUDT",
                List.of("order date", "purchase date"),
                "Purchase Order Date",
                "OrderDate");
    }

    private static void seedSearchManufacturingOrder(Map<String, List<SearchFieldDefinition>> fields) {
        String intent = "SearchManufacturingOrder";
        add(fields, intent, "MFNO",
                List.of("manufacturing order", "manufacturing order number", "mo number"),
                "Manufacturing Order Number",
                null);
        add(fields, intent, "PRNO",
                List.of("product", "product number"),
                "Product Number",
                null);
        add(fields, intent, "WHST",
                List.of("status", "manufacturing status"),
                "Manufacturing Status",
                null);
        add(fields, intent, "FACI",
                List.of("facility", "plant"),
                "Facility",
                null);
        add(fields, intent, "STDT",
                List.of("planned start", "start date"),
                "Planned Start Date",
                null);
        add(fields, intent, "FIDT",
                List.of("planned finish", "finish date", "end date"),
                "Planned Finish Date",
                null);
    }

    private static void seedSearchDistributionOrder(Map<String, List<SearchFieldDefinition>> fields) {
        String intent = "SearchDistributionOrder";
        add(fields, intent, "TRNR",
                List.of("distribution order", "distribution order number"),
                "Distribution Order Number",
                null);
        add(fields, intent, "WHLO",
                List.of("warehouse"),
                "Warehouse",
                null);
        add(fields, intent, "FACI",
                List.of("facility", "plant"),
                "Facility",
                null);
        add(fields, intent, "RESP",
                List.of("responsible", "assigned to"),
                "Responsible Person",
                null);
        add(fields, intent, "TRSH",
                List.of("status", "distribution status"),
                "Highest Distribution Status",
                null);
        add(fields, intent, "TRSL",
                List.of("lowest status"),
                "Lowest Distribution Status",
                null);
        add(fields, intent, "RIDT",
                List.of("receiving date"),
                "Receiving Date",
                null);
    }

    private static void seedGetCustomer(Map<String, List<SearchFieldDefinition>> fields) {
        add(fields, "GetCustomer", "CUNO",
                List.of("customer", "customer number", "customer id"),
                "Customer Number",
                null);
    }

    private static void seedGetCustomerFinancial(Map<String, List<SearchFieldDefinition>> fields) {
        add(fields, "GetCustomerFinancial", "CUNO",
                List.of("customer", "customer number", "customer id"),
                "Customer Number",
                null);
    }

    private static void add(
            Map<String, List<SearchFieldDefinition>> fields,
            String intentName,
            String m3Field,
            List<String> keywords,
            String description,
            String lexSlotName
    ) {
        fields.computeIfAbsent(intentName, ignored -> new ArrayList<>())
                .add(new SearchFieldDefinition(
                        intentName,
                        m3Field,
                        List.copyOf(keywords),
                        description,
                        lexSlotName
                ));
    }
}
