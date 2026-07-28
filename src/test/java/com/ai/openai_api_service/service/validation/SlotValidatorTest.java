package com.ai.openai_api_service.service.validation;

import com.ai.openai_api_service.service.SearchFieldCatalog;
import com.ai.openai_api_service.service.normalizer.FieldDefinitionRegistry;
import com.ai.openai_api_service.service.normalizer.SlotValue;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlotValidatorTest {

    private final SlotValidator validator = new SlotValidator(
            new SearchFieldCatalog(),
            new FieldDefinitionRegistry()
    );

    @Test
    void validate_warehouse_validLength() {
        var result = validator.validate(
                "SearchPurchaseOrder",
                Map.of("Warehouse", new SlotValue("A01"))
        ).getFirst();

        assertTrue(result.valid());
    }

    @Test
    void validate_warehouse_invalidLength() {
        var result = validator.validate(
                "SearchPurchaseOrder",
                Map.of("Warehouse", new SlotValue("Mahesh"))
        ).getFirst();

        assertFalse(result.valid());
        assertEquals("WHLO", result.m3Field());
        assertTrue(result.reason().contains("Expected length=3"));
    }

    @Test
    void validate_status_valid() {
        var result = validator.validate(
                "SearchPurchaseOrder",
                Map.of("HighestStatus", new SlotValue("33"))
        ).getFirst();

        assertTrue(result.valid());
    }

    @Test
    void validate_status_invalid() {
        var result = validator.validate(
                "SearchPurchaseOrder",
                Map.of("HighestStatus", new SlotValue("89033"))
        ).getFirst();

        assertFalse(result.valid());
        assertTrue(result.reason().contains("Expected length=2"));
    }

    @Test
    void validate_orderDate_isoAccepted() {
        var result = validator.validate(
                "SearchCustomerOrder",
                Map.of("OrderDate", new SlotValue("2026-04-24"))
        ).getFirst();

        assertTrue(result.valid());
    }

    @Test
    void validate_unknownSlot_passesThrough() {
        var result = validator.validate(
                "SearchCustomerOrder",
                Map.of("Unknown", new SlotValue("VALUE"))
        ).getFirst();

        assertTrue(result.valid());
    }

    @Test
    void validate_emptyMap_returnsEmpty() {
        assertTrue(validator.validate("SearchCustomerOrder", Map.of()).isEmpty());
    }
}
