package com.ai.openai_api_service.service;

import com.ai.openai_api_service.model.SearchCriterion;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SqryBuilderTest {

    private final SqryBuilder sqryBuilder = new SqryBuilder(new SearchValueFormatter());

    @Test
    void build_singleCriterion() {
        assertEquals(
                "CUNO:C00001",
                sqryBuilder.build(List.of(new SearchCriterion("CUNO", "C00001")))
        );
    }

    @Test
    void build_multipleCriteria_joinsWithAndAndFormatsDates() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("CUNO", "C00001");
        values.put("FACI", "A01");
        values.put("ORST", "33");
        values.put("ORDT", "2026-04-24");

        List<SearchCriterion> criteria = values.entrySet().stream()
                .map(entry -> new SearchCriterion(entry.getKey(), entry.getValue()))
                .toList();

        assertEquals(
                "CUNO:C00001 AND FACI:A01 AND ORST:33 AND ORDT:20260424",
                sqryBuilder.build(criteria)
        );
    }

    @Test
    void build_nullList_returnsEmpty() {
        assertEquals("", sqryBuilder.build(null));
    }

    @Test
    void build_emptyList_returnsEmpty() {
        assertEquals("", sqryBuilder.build(List.of()));
    }

    @Test
    void build_nullAndBlankValues_ignored() {
        List<SearchCriterion> criteria = new ArrayList<>();
        criteria.add(new SearchCriterion("CUNO", "C00001"));
        criteria.add(new SearchCriterion("FACI", null));
        criteria.add(new SearchCriterion("ORST", ""));
        criteria.add(new SearchCriterion("ORDT", "   "));
        criteria.add(new SearchCriterion(null, "VALUE"));
        criteria.add(new SearchCriterion("  ", "VALUE"));

        assertEquals("CUNO:C00001", sqryBuilder.build(criteria));
    }

    @Test
    void build_preservesOrder() {
        List<SearchCriterion> criteria = List.of(
                new SearchCriterion("FACI", "A01"),
                new SearchCriterion("CUNO", "C00001"),
                new SearchCriterion("ORST", "33")
        );

        assertEquals(
                "FACI:A01 AND CUNO:C00001 AND ORST:33",
                sqryBuilder.build(criteria)
        );
    }
}
