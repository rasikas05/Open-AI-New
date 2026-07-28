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
        addGuided(fields, intent, "ORNO",
                List.of("order", "customer order", "customer order number", "order number"),
                "Customer Order Number",
                "CustomerOrderNumber",
                1,
                "Please enter Customer Order Number.",
                "1000001234",
                List.of("order number", "customer order number", "orno"));
        addGuided(fields, intent, "CUNO",
                List.of("customer", "customer number", "customer id"),
                "Customer Number",
                "CustomerNumber",
                2,
                "Please enter Customer Number.",
                "C00001",
                List.of("customer no", "cuno"));
        addGuided(fields, intent, "FACI",
                List.of("facility", "plant"),
                "Facility",
                "Facility",
                3,
                "Please enter Facility.",
                "A01",
                List.of("plant"));
        addGuided(fields, intent, "SMCD",
                List.of("salesperson", "sales representative", "handled by"),
                "Salesperson",
                "Salesperson",
                4,
                "Please enter Salesperson.",
                "SP01",
                List.of("sales person", "sales representative"));
        addGuided(fields, intent, "RESP",
                List.of("responsible", "assigned to", "owner"),
                "Responsible Person",
                "Responsible",
                5,
                "Please enter Responsible Person.",
                "RP01",
                List.of("responsible", "owner"));
        addGuided(fields, intent, "ORST",
                List.of("status", "order status"),
                "Highest Order Status",
                "HighestStatus",
                6,
                "Please enter Highest Order Status.",
                "77",
                List.of("highest status", "high status", "status"));
        addGuided(fields, intent, "ORSL",
                List.of("lowest status"),
                "Lowest Order Status",
                "LowestStatus",
                7,
                "Please enter Lowest Order Status.",
                "22",
                List.of("lowest status", "low status"));
        addGuided(fields, intent, "ORDT",
                List.of("order date", "placed on", "date"),
                "Order Date",
                "OrderDate",
                8,
                "Please enter Order Date.",
                "2026-07-22",
                List.of("order date"));
        addGuided(fields, intent, "RLDZ",
                List.of("requested delivery date", "delivery date", "requested delivery"),
                "Requested Delivery Date",
                "RequestedDeliveryDate",
                9,
                "Please enter Requested Delivery Date.",
                "2026-07-22",
                List.of("requested delivery date", "delivery date", "rldz"));
        addGuided(fields, intent, "ORTP",
                List.of("order type", "type of order"),
                "Order Type",
                "OrderType",
                10,
                "Please enter Order Type.",
                "A01",
                List.of("type"));
        addGuided(fields, intent, "PYNO",
                List.of("payer"),
                "Payer",
                "Payer",
                11,
                "Please enter Payer.",
                "P00001",
                List.of("payer number", "pyno"));
    }

    private static void seedSearchPurchaseOrder(Map<String, List<SearchFieldDefinition>> fields) {
        String intent = "SearchPurchaseOrder";
        addGuided(fields, intent, "PUNO",
                List.of("purchase order", "purchase order number", "po number"),
                "Purchase Order Number",
                "PurchaseOrderNumber",
                1,
                "Please enter Purchase Order Number.",
                "4500012345",
                List.of("po number", "purchase order"));
        addGuided(fields, intent, "DIVI",
                List.of("division"),
                "Division",
                "Division",
                2,
                "Please enter Division.",
                "A01",
                List.of("divi"));
        addGuided(fields, intent, "WHLO",
                List.of("warehouse"),
                "Warehouse",
                "Warehouse",
                3,
                "Please enter Warehouse.",
                "001",
                List.of("wh"));
        addGuided(fields, intent, "SUNO",
                List.of("supplier", "supplier number", "vendor"),
                "Supplier",
                "Supplier",
                4,
                "Please enter Supplier.",
                "S00001",
                List.of("vendor", "supplier number"));
        addGuided(fields, intent, "PUSL",
                List.of("lowest status"),
                "Lowest Purchase Order Status",
                "LowestStatus",
                5,
                "Please enter Lowest Purchase Order Status.",
                "22",
                List.of("lowest status", "low status"));
        addGuided(fields, intent, "PUST",
                List.of("status", "purchase order status"),
                "Highest Purchase Order Status",
                "HighestStatus",
                6,
                "Please enter Highest Purchase Order Status.",
                "77",
                List.of("highest status", "status", "high status"));
        addGuided(fields, intent, "PUDT",
                List.of("order date", "purchase date"),
                "Purchase Order Date",
                "OrderDate",
                7,
                "Please enter Purchase Order Date.",
                "2026-07-22",
                List.of("order date", "purchase date"));
        addGuided(fields, intent, "BUYE",
                List.of("buyer", "purchaser"),
                "Buyer",
                "Buyer",
                8,
                "Please enter Buyer.",
                "B001",
                List.of("purchaser"));
        addGuided(fields, intent, "FACI",
                List.of("facility", "plant"),
                "Facility",
                "Facility",
                9,
                "Please enter Facility.",
                "A01",
                List.of("plant"));
        addGuided(fields, intent, "ORTY",
                List.of("order type", "purchase order type"),
                "Order Type",
                "OrderType",
                10,
                "Please enter Order Type.",
                "A01",
                List.of("type"));
        addGuided(fields, intent, "POTC",
                List.of("purchase category", "category"),
                "Purchase Category",
                "PurchaseCategory",
                11,
                "Please enter Purchase Category.",
                "CAT01",
                List.of("category"));
        addGuided(fields, intent, "PURC",
                List.of("requisition", "requisition by", "requisitioned by"),
                "Requisition By",
                "RequisitionBy",
                12,
                "Please enter Requisition By.",
                "RQ001",
                List.of("requisition by", "requisitioned by"));
    }

    private static void seedSearchManufacturingOrder(Map<String, List<SearchFieldDefinition>> fields) {
        String intent = "SearchManufacturingOrder";
        addGuided(fields, intent, "MFNO",
                List.of("manufacturing order", "manufacturing order number", "mo number"),
                "Manufacturing Order Number",
                "ManufacturingOrderNumber",
                1,
                "Please enter Manufacturing Order Number.",
                "2000012345",
                List.of("mo number", "manufacturing order"));
        addGuided(fields, intent, "PRNO",
                List.of("product", "product number"),
                "Product Number",
                "ProductNumber",
                2,
                "Please enter Product Number.",
                "P100001",
                List.of("product", "item"));
        addGuided(fields, intent, "FACI",
                List.of("facility", "plant"),
                "Facility",
                "Facility",
                3,
                "Please enter Facility.",
                "A01",
                List.of("plant"));
        addGuided(fields, intent, "WHST",
                List.of("status", "manufacturing status"),
                "Manufacturing Status",
                "ManufacturingStatus",
                4,
                "Please enter Manufacturing Status.",
                "33",
                List.of("status"));
        addGuided(fields, intent, "STDT",
                List.of("planned start", "start date"),
                "Planned Start Date",
                "PlannedStartDate",
                5,
                "Please enter Planned Start Date.",
                "2026-07-22",
                List.of("start date", "planned start"));
        addGuided(fields, intent, "FIDT",
                List.of("planned finish", "finish date", "end date"),
                "Planned Finish Date",
                "PlannedFinishDate",
                6,
                "Please enter Planned Finish Date.",
                "2026-07-29",
                List.of("finish date", "planned finish"));
        addGuided(fields, intent, "RORN",
                List.of("reference order", "reference order number"),
                "Reference Order Number",
                "ReferenceOrderNumber",
                7,
                "Please enter Reference Order Number.",
                "1000001234",
                List.of("reference order"));
        addGuided(fields, intent, "PRIO",
                List.of("priority"),
                "Priority",
                "Priority",
                8,
                "Please enter Priority.",
                "1",
                List.of("prio"));
    }

    private static void seedSearchDistributionOrder(Map<String, List<SearchFieldDefinition>> fields) {
        String intent = "SearchDistributionOrder";
        addGuided(fields, intent, "TRNR",
                List.of("distribution order", "distribution order number"),
                "Distribution Order Number",
                "DistributionOrderNumber",
                1,
                "Please enter Distribution Order Number.",
                "3000012345",
                List.of("do number", "distribution order"));
        addGuided(fields, intent, "FACI",
                List.of("facility", "plant"),
                "Facility",
                "Facility",
                2,
                "Please enter Facility.",
                "A01",
                List.of("plant"));
        addGuided(fields, intent, "WHLO",
                List.of("warehouse"),
                "Warehouse",
                "Warehouse",
                3,
                "Please enter Warehouse.",
                "001",
                List.of("wh"));
        addGuided(fields, intent, "TRTP",
                List.of("order type", "distribution order type"),
                "Order Type",
                "OrderType",
                4,
                "Please enter Order Type.",
                "A01",
                List.of("type"));
        addGuided(fields, intent, "RESP",
                List.of("responsible", "assigned to"),
                "Responsible Person",
                "Responsible",
                5,
                "Please enter Responsible Person.",
                "RP01",
                List.of("responsible", "owner"));
        addGuided(fields, intent, "TRSH",
                List.of("status", "distribution status"),
                "Highest Distribution Status",
                "HighestStatus",
                6,
                "Please enter Highest Distribution Status.",
                "77",
                List.of("highest status", "status", "high status"));
        addGuided(fields, intent, "TRSL",
                List.of("lowest status"),
                "Lowest Distribution Status",
                "LowestStatus",
                7,
                "Please enter Lowest Distribution Status.",
                "22",
                List.of("lowest status", "low status"));
        addGuided(fields, intent, "RIDT",
                List.of("receiving date"),
                "Receiving Date",
                "ReceivingDate",
                8,
                "Please enter Receiving Date.",
                "2026-07-22",
                List.of("receiving date"));
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

    private static void addGuided(
            Map<String, List<SearchFieldDefinition>> fields,
            String intentName,
            String m3Field,
            List<String> keywords,
            String description,
            String lexSlotName,
            int displayOrder,
            String prompt,
            String example,
            List<String> aliases
    ) {
        fields.computeIfAbsent(intentName, ignored -> new ArrayList<>())
                .add(new SearchFieldDefinition(
                        intentName,
                        m3Field,
                        List.copyOf(keywords),
                        description,
                        lexSlotName,
                        displayOrder,
                        prompt,
                        example,
                        aliases != null ? List.copyOf(aliases) : List.of()
                ));
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
