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
        slots.put("Status", "33");
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
        slots.put("Status", "33");

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
        slots.put("Status", "   ");
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
    void resolve_status_mapsToOrst() {
        assertEquals(
                List.of(new SearchCriterion("ORST", "33")),
                resolver.resolve("SearchCustomerOrder", Map.of("Status", "33"))
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
    void resolve_customerOrderNumber_mapsToOrno() {
        assertEquals(
                List.of(new SearchCriterion("ORNO", "0010000864")),
                resolver.resolve("SearchCustomerOrder", Map.of("CustomerOrderNumber", "0010000864"))
        );
    }
}
