package com.ai.openai_api_service.service;

import com.ai.openai_api_service.model.IntentDefinition;
import com.ai.openai_api_service.model.RequestType;
import com.ai.openai_api_service.model.SearchCriterion;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class M3RequestBuilderTest {

    private final M3RequestBuilder builder = new M3RequestBuilder(
            new SqryBuilder(new SearchValueFormatter())
    );

    private static final IntentDefinition SEARCH_CUSTOMER_ORDER = new IntentDefinition(
            "SearchCustomerOrder",
            "OIS100MI",
            "SearchHead",
            RequestType.SEARCH,
            "SQRY"
    );

    private static final IntentDefinition SEARCH_PURCHASE_ORDER = new IntentDefinition(
            "SearchPurchaseOrder",
            "PPS200MI",
            "SearchHead",
            RequestType.SEARCH,
            "SQRY"
    );

    @Test
    void build_searchCustomerOrder_populatesProgramTransactionAndSqry() {
        List<SearchCriterion> criteria = criteria(
                "CUNO", "C00001",
                "FACI", "A01",
                "ORST", "33",
                "ORDT", "2026-04-24"
        );

        LexIntentMapper.MappedM3Request mapped = builder.build(SEARCH_CUSTOMER_ORDER, criteria);

        assertEquals("OIS100MI", mapped.program());
        assertEquals("SearchHead", mapped.transaction());
        assertEquals("search", mapped.actionTaken());
        assertEquals(
                "CUNO:C00001 AND FACI:A01 AND ORST:33 AND ORDT:20260424",
                mapped.params().get("SQRY")
        );
    }

    @Test
    void build_multipleCriteria_buildsExpectedSqry() {
        List<SearchCriterion> criteria = List.of(
                new SearchCriterion("CUNO", "C00001"),
                new SearchCriterion("FACI", "A01")
        );

        LexIntentMapper.MappedM3Request mapped = builder.build(SEARCH_CUSTOMER_ORDER, criteria);

        assertEquals("CUNO:C00001 AND FACI:A01", mapped.params().get("SQRY"));
    }

    @Test
    void build_emptyCriteria_omitsSqry() {
        LexIntentMapper.MappedM3Request mapped = builder.build(SEARCH_CUSTOMER_ORDER, List.of());

        assertTrue(mapped.params().isEmpty());
        assertFalse(mapped.params().containsKey("SQRY"));
    }

    @Test
    void build_nullCriteria_omitsSqry() {
        LexIntentMapper.MappedM3Request mapped = builder.build(SEARCH_CUSTOMER_ORDER, null);

        assertTrue(mapped.params().isEmpty());
        assertFalse(mapped.params().containsKey("SQRY"));
    }

    @Test
    void build_searchPurchaseOrder_usesIntentDefinitionProgram() {
        List<SearchCriterion> criteria = List.of(new SearchCriterion("PUNO", "PO12345"));

        LexIntentMapper.MappedM3Request mapped = builder.build(SEARCH_PURCHASE_ORDER, criteria);

        assertEquals("PPS200MI", mapped.program());
        assertEquals("SearchHead", mapped.transaction());
        assertEquals("PUNO:PO12345", mapped.params().get("SQRY"));
        assertEquals("search", mapped.actionTaken());
    }

    private static List<SearchCriterion> criteria(String... fieldValuePairs) {
        Map<String, String> values = new LinkedHashMap<>();
        for (int i = 0; i < fieldValuePairs.length; i += 2) {
            values.put(fieldValuePairs[i], fieldValuePairs[i + 1]);
        }
        return values.entrySet().stream()
                .map(entry -> new SearchCriterion(entry.getKey(), entry.getValue()))
                .toList();
    }
}
