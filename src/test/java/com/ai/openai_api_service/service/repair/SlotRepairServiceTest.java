package com.ai.openai_api_service.service.repair;

import com.ai.openai_api_service.service.SearchFieldCatalog;
import com.ai.openai_api_service.service.normalizer.FieldDefinitionRegistry;
import com.ai.openai_api_service.service.normalizer.SlotValue;
import com.ai.openai_api_service.service.repair.rules.KeywordUtteranceRepairRule;
import com.ai.openai_api_service.service.repair.rules.MergedStatusSplitRule;
import com.ai.openai_api_service.service.repair.rules.MergedTextSplitRule;
import com.ai.openai_api_service.service.repair.rules.MisassignmentRepairRule;
import com.ai.openai_api_service.service.validation.SlotValidator;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlotRepairServiceTest {

    private final SlotRepairService repairService = createRepairService();

    private static SlotRepairService createRepairService() {
        SearchFieldCatalog catalog = new SearchFieldCatalog();
        FieldDefinitionRegistry registry = new FieldDefinitionRegistry();
        SlotValidator validator = new SlotValidator(catalog, registry);
        SlotKeywordRegistry keywordRegistry = new SlotKeywordRegistry(catalog);
        return new SlotRepairService(
                validator,
                catalog,
                registry,
                new KeywordUtteranceRepairRule(keywordRegistry),
                new MisassignmentRepairRule(),
                new MergedStatusSplitRule(keywordRegistry),
                new MergedTextSplitRule(keywordRegistry)
        );
    }

    @Test
    void repair_misassignment_movesWarehouseToSupplier() {
        Map<String, SlotValue> slots = new LinkedHashMap<>();
        slots.put("Warehouse", new SlotValue("MAHESH"));

        Map<String, SlotValue> repaired = repairService.repair("SearchPurchaseOrder", null, slots);

        assertNull(repaired.get("Warehouse"));
        assertEquals("MAHESH", repaired.get("Supplier").value());
    }

    @Test
    void repair_mergedStatusSplit() {
        Map<String, SlotValue> slots = new LinkedHashMap<>();
        slots.put("Status", new SlotValue("89033"));

        Map<String, SlotValue> repaired = repairService.repair("SearchPurchaseOrder", null, slots);

        assertEquals("33", repaired.get("Status").value());
        assertEquals("890", repaired.get("PurchaseOrderNumber").value());
    }

    @Test
    void repair_mergedTextSplit() {
        Map<String, SlotValue> slots = new LinkedHashMap<>();
        slots.put("PurchaseOrderNumber", new SlotValue("MAHESH SUPPLIER"));

        Map<String, SlotValue> repaired = repairService.repair("SearchPurchaseOrder", null, slots);

        assertEquals("MAHESH", repaired.get("Supplier").value());
        assertFalse(repaired.containsKey("PurchaseOrderNumber"));
    }

    @Test
    void repair_keywordFromUtterance() {
        Map<String, SlotValue> slots = new LinkedHashMap<>();
        slots.put("Warehouse", new SlotValue("MAHESH"));

        Map<String, SlotValue> repaired = repairService.repair(
                "SearchPurchaseOrder",
                "find PO for supplier mahesh warehouse A01",
                slots
        );

        assertEquals("MAHESH", repaired.get("Supplier").value());
        assertEquals("A01", repaired.get("Warehouse").value());
    }

    @Test
    void repair_alreadyValid_unchanged() {
        Map<String, SlotValue> slots = new LinkedHashMap<>();
        slots.put("Warehouse", new SlotValue("A01"));
        slots.put("Status", new SlotValue("33"));

        Map<String, SlotValue> repaired = repairService.repair("SearchPurchaseOrder", null, slots);

        assertEquals(slots, repaired);
    }

    @Test
    void repair_idempotentOnSecondPass() {
        Map<String, SlotValue> slots = new LinkedHashMap<>();
        slots.put("Warehouse", new SlotValue("MAHESH"));

        Map<String, SlotValue> once = repairService.repair("SearchPurchaseOrder", null, slots);
        Map<String, SlotValue> twice = repairService.repair("SearchPurchaseOrder", null, once);

        assertEquals(once, twice);
    }

    @Test
    void repair_mergedStatusSplit_doesNotInventOrderWithoutContext() {
        Map<String, SlotValue> slots = new LinkedHashMap<>();
        slots.put("Facility", new SlotValue("A01"));
        slots.put("CustomerNumber", new SlotValue("Y11100"));
        slots.put("Status", new SlotValue("3320250433"));

        Map<String, SlotValue> repaired = repairService.repair(
                "SearchCustomerOrder",
                "Retrieve customer orders for customer Y11100 in facility A01 with status 33 on 2025-04-24",
                slots
        );

        assertEquals("33", repaired.get("Status").value());
        assertFalse(repaired.containsKey("CustomerOrderNumber"));
    }

    @Test
    void repair_mergedStatusSplit_assignsOrderWhenUtteranceMentionsOrderNumber() {
        Map<String, SlotValue> slots = new LinkedHashMap<>();
        slots.put("Status", new SlotValue("001000086433"));

        Map<String, SlotValue> repaired = repairService.repair(
                "SearchCustomerOrder",
                "find customer order number 0010000864 with status 33",
                slots
        );

        assertEquals("33", repaired.get("Status").value());
        assertEquals("0010000864", repaired.get("CustomerOrderNumber").value());
        assertFalse(repaired.containsKey("CustomerNumber"));
    }

    @Test
    void repair_keywordFromUtterance_customerOrder_doesNotAssignCustomerNumberFromOrderKeyword() {
        Map<String, SlotValue> slots = new LinkedHashMap<>();
        slots.put("CustomerOrderNumber", new SlotValue("1000001234status"));

        Map<String, SlotValue> repaired = repairService.repair(
                "SearchCustomerOrder",
                "Show customer order 1000001234 status",
                slots
        );

        assertEquals("1000001234", repaired.get("CustomerOrderNumber").value());
        assertFalse(repaired.containsKey("CustomerNumber"));
    }

    @Test
    void repair_orderStatusForCustomer_doesNotCaptureForAsStatus() {
        Map<String, SlotValue> slots = new LinkedHashMap<>();
        slots.put("CustomerNumber", new SlotValue("Y11100"));
        slots.put("OrderDate", new SlotValue("5"));

        String utterance = "Show order status for customer Y11100 last 5 orders";

        Map<String, SlotValue> repaired = repairService.repair("SearchCustomerOrder", utterance, slots);

        assertFalse(repaired.containsKey("Status"));
        assertFalse(repaired.containsKey("CustomerOrderNumber"));
        assertEquals("Y11100", repaired.get("CustomerNumber").value());
    }

    @Test
    void repair_mergedStatusSplit_doesNotSplitNonNumericStatus() {
        Map<String, SlotValue> slots = new LinkedHashMap<>();
        slots.put("Status", new SlotValue("FOR"));

        Map<String, SlotValue> repaired = repairService.repair("SearchCustomerOrder", null, slots);

        assertEquals("FOR", repaired.get("Status").value());
        assertFalse(repaired.containsKey("CustomerOrderNumber"));
    }

    @Test
    void repair_respectsMaxIterations() {
        Map<String, SlotValue> slots = new LinkedHashMap<>();
        slots.put("Warehouse", new SlotValue("MAHESH"));
        slots.put("Status", new SlotValue("89033"));

        Map<String, SlotValue> repaired = repairService.repair("SearchPurchaseOrder", null, slots);

        assertEquals(Set.of("Supplier", "Status", "PurchaseOrderNumber"), repaired.keySet());
    }
}
