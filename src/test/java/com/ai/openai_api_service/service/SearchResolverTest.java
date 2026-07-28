package com.ai.openai_api_service.service;

import com.ai.openai_api_service.model.SearchCriterion;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchResolverTest {

    private final SearchResolver resolver = new SearchResolver(new SearchFieldCatalog());

    @Test
    void resolve_searchCustomerOrder_mapsAllPopulatedSlots() {
        Map<String, String> slots = new LinkedHashMap<>();
        slots.put("CustomerNumber", "C00001");
        slots.put("CustomerOrderNumber", "0010000864");
        slots.put("Facility", "A01");
        slots.put("HighestStatus", "33");
        slots.put("Salesperson", "FABSALES1");
        slots.put("Responsible", "MAHESHM");
        slots.put("OrderDate", "2026-05-05");

        List<SearchCriterion> criteria = resolver.resolve("SearchCustomerOrder", slots);

        assertEquals(List.of(
                new SearchCriterion("CUNO", "C00001"),
                new SearchCriterion("ORNO", "0010000864"),
                new SearchCriterion("FACI", "A01"),
                new SearchCriterion("ORST", "33"),
                new SearchCriterion("SMCD", "FABSALES1"),
                new SearchCriterion("RESP", "MAHESHM"),
                new SearchCriterion("ORDT", "2026-05-05")
        ), criteria);
    }

    @Test
    void resolve_nullSlotsAreIgnored() {
        Map<String, String> slots = new HashMap<>();
        slots.put("CustomerNumber", "C00001");
        slots.put("Facility", null);
        slots.put("HighestStatus", "33");

        List<SearchCriterion> criteria = resolver.resolve("SearchCustomerOrder", slots);

        assertEquals(2, criteria.size());
        assertTrue(criteria.contains(new SearchCriterion("CUNO", "C00001")));
        assertTrue(criteria.contains(new SearchCriterion("ORST", "33")));
    }

    @Test
    void resolve_emptySlotsAreIgnored() {
        Map<String, String> slots = new LinkedHashMap<>();
        slots.put("CustomerNumber", "C00001");
        slots.put("Facility", "");
        slots.put("HighestStatus", "   ");
        slots.put("Salesperson", "FABSALES1");

        List<SearchCriterion> criteria = resolver.resolve("SearchCustomerOrder", slots);

        assertEquals(List.of(
                new SearchCriterion("CUNO", "C00001"),
                new SearchCriterion("SMCD", "FABSALES1")
        ), criteria);
    }

    @Test
    void resolve_unknownSlotsAreIgnored() {
        Map<String, String> slots = new LinkedHashMap<>();
        slots.put("CustomerNumber", "C00001");
        slots.put("UnknownSlot", "VALUE");

        List<SearchCriterion> criteria = resolver.resolve("SearchCustomerOrder", slots);

        assertEquals(List.of(new SearchCriterion("CUNO", "C00001")), criteria);
    }

    @Test
    void resolve_nullOrBlankIntent_returnsEmpty() {
        assertTrue(resolver.resolve(null, Map.of("CustomerNumber", "C00001")).isEmpty());
        assertTrue(resolver.resolve("  ", Map.of("CustomerNumber", "C00001")).isEmpty());
        assertTrue(resolver.resolve("SearchCustomerOrder", null).isEmpty());
    }

    @Test
    void resolve_customerNumber_mapsToCuno() {
        assertEquals(
                List.of(new SearchCriterion("CUNO", "C00001")),
                resolver.resolve("SearchCustomerOrder", Map.of("CustomerNumber", "C00001"))
        );
    }

    @Test
    void resolve_facility_mapsToFaci() {
        assertEquals(
                List.of(new SearchCriterion("FACI", "A01")),
                resolver.resolve("SearchCustomerOrder", Map.of("Facility", "A01"))
        );
    }

    @Test
    void resolve_highestStatus_mapsToOrst() {
        assertEquals(
                List.of(new SearchCriterion("ORST", "33")),
                resolver.resolve("SearchCustomerOrder", Map.of("HighestStatus", "33"))
        );
    }

    @Test
    void resolve_lowestStatus_mapsToOrsl() {
        assertEquals(
                List.of(new SearchCriterion("ORSL", "22")),
                resolver.resolve("SearchCustomerOrder", Map.of("LowestStatus", "22"))
        );
    }

    @Test
    void resolve_salesperson_mapsToSmcd() {
        assertEquals(
                List.of(new SearchCriterion("SMCD", "FABSALES1")),
                resolver.resolve("SearchCustomerOrder", Map.of("Salesperson", "FABSALES1"))
        );
    }

    @Test
    void resolve_responsible_mapsToResp() {
        assertEquals(
                List.of(new SearchCriterion("RESP", "MAHESHM")),
                resolver.resolve("SearchCustomerOrder", Map.of("Responsible", "MAHESHM"))
        );
    }

    @Test
    void resolve_orderDate_mapsToOrdt() {
        assertEquals(
                List.of(new SearchCriterion("ORDT", "2026-05-05")),
                resolver.resolve("SearchCustomerOrder", Map.of("OrderDate", "2026-05-05"))
        );
    }

    @Test
    void resolve_m3FieldKey_mapsWhenLexSlotMissing() {
        assertEquals(
                List.of(new SearchCriterion("ORNO", "1000001234")),
                resolver.resolve("SearchCustomerOrder", Map.of("ORNO", "1000001234"))
        );
    }

    @Test
    void resolve_distributionOrder_m3FieldKey_mapsTrnr() {
        assertEquals(
                List.of(new SearchCriterion("TRNR", "DO00000001")),
                resolver.resolve("SearchDistributionOrder", Map.of("TRNR", "DO00000001"))
        );
    }

    @Test
    void resolve_distributionOrder_warehouse_mapsToWhlo() {
        assertEquals(
                List.of(new SearchCriterion("WHLO", "A01")),
                resolver.resolve("SearchDistributionOrder", Map.of("Warehouse", "A01"))
        );
    }

    @Test
    void resolve_distributionOrder_facility_mapsToFaci() {
        assertEquals(
                List.of(new SearchCriterion("FACI", "A01")),
                resolver.resolve("SearchDistributionOrder", Map.of("Facility", "A01"))
        );
    }

    @Test
    void resolve_manufacturingOrder_productNumber_mapsToPrno() {
        assertEquals(
                List.of(new SearchCriterion("PRNO", "P10001")),
                resolver.resolve("SearchManufacturingOrder", Map.of("ProductNumber", "P10001"))
        );
    }

    @Test
    void resolve_manufacturingOrder_manufacturingOrderNumber_mapsToMfno() {
        assertEquals(
                List.of(new SearchCriterion("MFNO", "MO0001")),
                resolver.resolve("SearchManufacturingOrder", Map.of("ManufacturingOrderNumber", "MO0001"))
        );
    }

    @Test
    void resolve_manufacturingOrder_facility_mapsToFaci() {
        assertEquals(
                List.of(new SearchCriterion("FACI", "A01")),
                resolver.resolve("SearchManufacturingOrder", Map.of("Facility", "A01"))
        );
    }

    @Test
    void resolve_manufacturingOrder_referenceOrderNumber_mapsToRorn() {
        assertEquals(
                List.of(new SearchCriterion("RORN", "CO12345")),
                resolver.resolve("SearchManufacturingOrder", Map.of("ReferenceOrderNumber", "CO12345"))
        );
    }

    @Test
    void resolve_distributionOrder_distributionOrderNumber_mapsToTrnr() {
        assertEquals(
                List.of(new SearchCriterion("TRNR", "DO001")),
                resolver.resolve("SearchDistributionOrder", Map.of("DistributionOrderNumber", "DO001"))
        );
    }

    @Test
    void resolve_purchaseOrder_division_mapsToDivi() {
        assertEquals(
                List.of(new SearchCriterion("DIVI", "AAA")),
                resolver.resolve("SearchPurchaseOrder", Map.of("Division", "AAA"))
        );
    }
}
