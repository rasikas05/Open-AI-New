package com.ai.openai_api_service.service.normalizer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FieldDefinitionRegistryTest {

    private final FieldDefinitionRegistry registry = new FieldDefinitionRegistry();

    @Test
    void get_cuno_presentWithRepairReadyMetadata() {
        FieldDefinition definition = registry.get("CUNO").orElseThrow();

        assertEquals("CUNO", definition.fieldName());
        assertEquals(CaseStrategy.UPPER, definition.caseStrategy());
        assertEquals(FieldType.IDENTIFIER, definition.fieldType());
        assertEquals(FieldRole.PARTY, definition.repairRole());
        assertEquals(10, definition.maxLength());
        assertNotNull(definition.regexPattern());
        assertNotNull(definition.formatter());
    }

    @Test
    void get_whlo_isWarehouseCode() {
        FieldDefinition definition = registry.get("WHLO").orElseThrow();

        assertEquals(FieldRole.WAREHOUSE, definition.repairRole());
        assertEquals(FieldType.CODE, definition.fieldType());
        assertEquals(3, definition.expectedLength());
    }

    @Test
    void get_ordt_isDateWithoutFormatter() {
        FieldDefinition definition = registry.get("ORDT").orElseThrow();

        assertEquals(FieldType.DATE, definition.fieldType());
        assertEquals(FieldRole.DATE, definition.repairRole());
        assertEquals(CaseStrategy.NONE, definition.caseStrategy());
        assertTrue(definition.formatter() == null);
    }

    @Test
    void get_unknown_returnsEmpty() {
        assertTrue(registry.get("UNKNOWN").isEmpty());
        assertTrue(registry.get(null).isEmpty());
    }
}
