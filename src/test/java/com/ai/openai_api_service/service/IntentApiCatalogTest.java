package com.ai.openai_api_service.service;

import com.ai.openai_api_service.model.IntentDefinition;
import com.ai.openai_api_service.model.RequestType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntentApiCatalogTest {

    private final IntentApiCatalog catalog = new IntentApiCatalog();

    @Test
    void find_getCustomer_returnsReadDefinition() {
        IntentDefinition definition = catalog.find("GetCustomer").orElseThrow();

        assertEquals("GetCustomer", definition.intentName());
        assertEquals("CRS610MI", definition.program());
        assertEquals("GetBasicData", definition.transaction());
        assertEquals(RequestType.READ, definition.requestType());
        assertEquals("CUNO", definition.primaryParameter());
    }

    @Test
    void find_getCustomerFinancial_returnsReadDefinition() {
        IntentDefinition definition = catalog.find("GetCustomerFinancial").orElseThrow();

        assertEquals("CRS610MI", definition.program());
        assertEquals("GetFinancial", definition.transaction());
        assertEquals(RequestType.READ, definition.requestType());
        assertEquals("CUNO", definition.primaryParameter());
    }

    @Test
    void find_searchCustomerOrder_returnsSearchDefinition() {
        IntentDefinition definition = catalog.find("SearchCustomerOrder").orElseThrow();

        assertEquals("OIS100MI", definition.program());
        assertEquals("SearchHead", definition.transaction());
        assertEquals(RequestType.SEARCH, definition.requestType());
        assertEquals("SQRY", definition.primaryParameter());
    }

    @Test
    void find_searchPurchaseOrder_returnsSearchDefinition() {
        IntentDefinition definition = catalog.find("SearchPurchaseOrder").orElseThrow();

        assertEquals("PPS200MI", definition.program());
        assertEquals("SearchHead", definition.transaction());
        assertEquals(RequestType.SEARCH, definition.requestType());
        assertEquals("SQRY", definition.primaryParameter());
    }

    @Test
    void find_searchManufacturingOrder_returnsSearchDefinition() {
        IntentDefinition definition = catalog.find("SearchManufacturingOrder").orElseThrow();

        assertEquals("PMS100MI", definition.program());
        assertEquals("SearchMO", definition.transaction());
        assertEquals(RequestType.SEARCH, definition.requestType());
        assertEquals("SQRY", definition.primaryParameter());
    }

    @Test
    void find_searchDistributionOrder_returnsSearchDefinition() {
        IntentDefinition definition = catalog.find("SearchDistributionOrder").orElseThrow();

        assertEquals("MMS100MI", definition.program());
        assertEquals("SearchHead", definition.transaction());
        assertEquals(RequestType.SEARCH, definition.requestType());
        assertEquals("SQRY", definition.primaryParameter());
    }

    @Test
    void contains_allSixSeededIntents() {
        assertTrue(catalog.contains("GetCustomer"));
        assertTrue(catalog.contains("GetCustomerFinancial"));
        assertTrue(catalog.contains("SearchCustomerOrder"));
        assertTrue(catalog.contains("SearchPurchaseOrder"));
        assertTrue(catalog.contains("SearchManufacturingOrder"));
        assertTrue(catalog.contains("SearchDistributionOrder"));
    }
}
