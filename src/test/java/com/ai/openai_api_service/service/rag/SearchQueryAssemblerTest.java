package com.ai.openai_api_service.service.rag;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SearchQueryAssemblerTest {

    @Test
    void assemble_putsSanitizedFirstThenRewrites() {
        List<String> result = SearchQueryAssembler.assemble(
                "pricing issue",
                List.of("customer pricing configuration", "price list setup"),
                4
        );
        assertEquals(
                List.of("pricing issue", "customer pricing configuration", "price list setup"),
                result
        );
    }

    @Test
    void assemble_dedupesCaseInsensitiveAndSkipsBlanks() {
        List<String> result = SearchQueryAssembler.assemble(
                "  Pricing Issue  ",
                List.of("pricing issue", "  ", "Price List Setup", "other query"),
                4
        );
        assertEquals(List.of("Pricing Issue", "Price List Setup", "other query"), result);
    }

    @Test
    void assemble_respectsMaxQueries() {
        List<String> result = SearchQueryAssembler.assemble(
                "original",
                List.of("a", "b", "c", "d"),
                4
        );
        assertEquals(List.of("original", "a", "b", "c"), result);
    }
}
