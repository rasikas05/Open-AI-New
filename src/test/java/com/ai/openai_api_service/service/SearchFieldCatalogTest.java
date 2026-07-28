package com.ai.openai_api_service.service;

import com.ai.openai_api_service.model.SearchFieldDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchFieldCatalogTest {

    private final SearchFieldCatalog catalog = new SearchFieldCatalog();

    @Test
    void searchCustomerOrder_containsCunoAndOrno() {
        assertTrue(catalog.contains("SearchCustomerOrder", "CUNO"));
        assertTrue(catalog.contains("SearchCustomerOrder", "ORNO"));
    }

    @Test
    void searchPurchaseOrder_containsSunoAndBuye() {
        assertTrue(catalog.contains("SearchPurchaseOrder", "SUNO"));
        assertTrue(catalog.contains("SearchPurchaseOrder", "BUYE"));
    }

    @Test
    void searchManufacturingOrder_containsMfnoAndPrno() {
        assertTrue(catalog.contains("SearchManufacturingOrder", "MFNO"));
        assertTrue(catalog.contains("SearchManufacturingOrder", "PRNO"));
    }

    @Test
    void searchDistributionOrder_containsTrnrAndWhlo() {
        assertTrue(catalog.contains("SearchDistributionOrder", "TRNR"));
        assertTrue(catalog.contains("SearchDistributionOrder", "WHLO"));
    }

    @Test
    void getCustomer_containsCuno() {
        assertTrue(catalog.contains("GetCustomer", "CUNO"));
    }

    @Test
    void getCustomerFinancial_containsCuno() {
        assertTrue(catalog.contains("GetCustomerFinancial", "CUNO"));
    }

    @Test
    void searchCustomerOrder_cuno_keywordsAndDescription() {
        SearchFieldDefinition definition = catalog.find("SearchCustomerOrder", "CUNO").orElseThrow();

        assertEquals("SearchCustomerOrder", definition.intentName());
        assertEquals("CUNO", definition.m3Field());
        assertEquals("Customer Number", definition.description());
        assertEquals(List.of("customer", "customer number", "customer id"), definition.keywords());
        assertEquals("CustomerNumber", definition.lexSlotName());
    }

    @Test
    void searchCustomerOrder_findBySlot_mapsLexSlotsToM3Fields() {
        assertEquals("CUNO", catalog.findBySlot("SearchCustomerOrder", "CustomerNumber").orElseThrow().m3Field());
        assertEquals("ORNO", catalog.findBySlot("SearchCustomerOrder", "CustomerOrderNumber").orElseThrow().m3Field());
        assertEquals("FACI", catalog.findBySlot("SearchCustomerOrder", "Facility").orElseThrow().m3Field());
        assertEquals("ORST", catalog.findBySlot("SearchCustomerOrder", "HighestStatus").orElseThrow().m3Field());
        assertEquals("SMCD", catalog.findBySlot("SearchCustomerOrder", "Salesperson").orElseThrow().m3Field());
        assertEquals("RESP", catalog.findBySlot("SearchCustomerOrder", "Responsible").orElseThrow().m3Field());
        assertEquals("ORDT", catalog.findBySlot("SearchCustomerOrder", "OrderDate").orElseThrow().m3Field());
        assertEquals("RLDZ", catalog.findBySlot("SearchCustomerOrder", "RequestedDeliveryDate").orElseThrow().m3Field());
        assertEquals("ORTP", catalog.findBySlot("SearchCustomerOrder", "OrderType").orElseThrow().m3Field());
        assertEquals("ORSL", catalog.findBySlot("SearchCustomerOrder", "LowestStatus").orElseThrow().m3Field());
    }

    @Test
    void searchCustomerOrder_findBySlot_unknownSlot_returnsEmpty() {
        assertTrue(catalog.findBySlot("SearchCustomerOrder", "UnknownSlot").isEmpty());
    }

    @Test
    void searchPurchaseOrder_findBySlot_mapsLexSlotsToM3Fields() {
        assertEquals("SUNO", catalog.findBySlot("SearchPurchaseOrder", "Supplier").orElseThrow().m3Field());
        assertEquals("WHLO", catalog.findBySlot("SearchPurchaseOrder", "Warehouse").orElseThrow().m3Field());
        assertEquals("PUST", catalog.findBySlot("SearchPurchaseOrder", "HighestStatus").orElseThrow().m3Field());
        assertEquals("PUDT", catalog.findBySlot("SearchPurchaseOrder", "OrderDate").orElseThrow().m3Field());
        assertEquals("PUNO", catalog.findBySlot("SearchPurchaseOrder", "PurchaseOrderNumber").orElseThrow().m3Field());
        assertEquals("BUYE", catalog.findBySlot("SearchPurchaseOrder", "Buyer").orElseThrow().m3Field());
        assertEquals("FACI", catalog.findBySlot("SearchPurchaseOrder", "Facility").orElseThrow().m3Field());
        assertEquals("DIVI", catalog.findBySlot("SearchPurchaseOrder", "Division").orElseThrow().m3Field());
        assertEquals("ORTY", catalog.findBySlot("SearchPurchaseOrder", "OrderType").orElseThrow().m3Field());
        assertEquals("POTC", catalog.findBySlot("SearchPurchaseOrder", "PurchaseCategory").orElseThrow().m3Field());
        assertEquals("PURC", catalog.findBySlot("SearchPurchaseOrder", "RequisitionBy").orElseThrow().m3Field());
        assertEquals("PUSL", catalog.findBySlot("SearchPurchaseOrder", "LowestStatus").orElseThrow().m3Field());
    }

    @Test
    void searchDistributionOrder_findBySlot_mapsWarehouseAndFacility() {
        assertEquals("WHLO", catalog.findBySlot("SearchDistributionOrder", "Warehouse").orElseThrow().m3Field());
        assertEquals("FACI", catalog.findBySlot("SearchDistributionOrder", "Facility").orElseThrow().m3Field());
        assertEquals("TRNR", catalog.findBySlot("SearchDistributionOrder", "DistributionOrderNumber").orElseThrow().m3Field());
        assertEquals("RESP", catalog.findBySlot("SearchDistributionOrder", "Responsible").orElseThrow().m3Field());
        assertEquals("RIDT", catalog.findBySlot("SearchDistributionOrder", "ReceivingDate").orElseThrow().m3Field());
        assertEquals("TRTP", catalog.findBySlot("SearchDistributionOrder", "OrderType").orElseThrow().m3Field());
    }

    @Test
    void searchManufacturingOrder_findBySlot_mapsLexSlotsToM3Fields() {
        assertEquals("PRNO", catalog.findBySlot("SearchManufacturingOrder", "ProductNumber").orElseThrow().m3Field());
        assertEquals("MFNO", catalog.findBySlot("SearchManufacturingOrder", "ManufacturingOrderNumber").orElseThrow().m3Field());
        assertEquals("WHST", catalog.findBySlot("SearchManufacturingOrder", "ManufacturingStatus").orElseThrow().m3Field());
        assertEquals("FACI", catalog.findBySlot("SearchManufacturingOrder", "Facility").orElseThrow().m3Field());
        assertEquals("STDT", catalog.findBySlot("SearchManufacturingOrder", "PlannedStartDate").orElseThrow().m3Field());
        assertEquals("FIDT", catalog.findBySlot("SearchManufacturingOrder", "PlannedFinishDate").orElseThrow().m3Field());
        assertEquals("RORN", catalog.findBySlot("SearchManufacturingOrder", "ReferenceOrderNumber").orElseThrow().m3Field());
        assertEquals("PRIO", catalog.findBySlot("SearchManufacturingOrder", "Priority").orElseThrow().m3Field());
    }
}
