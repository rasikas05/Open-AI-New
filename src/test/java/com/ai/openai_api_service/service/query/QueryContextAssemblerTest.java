package com.ai.openai_api_service.service.query;

import com.ai.openai_api_service.model.QueryContext;
import com.ai.openai_api_service.model.SearchCriterion;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryContextAssemblerTest {

    private final QueryContextAssembler assembler = new QueryContextAssembler();

    @Test
    void assembleSearch_populatesIntentSlotsAndCriteria() {
        Map<String, String> slots = Map.of("CustomerNumber", "Y11100", "Status", "33");
        List<SearchCriterion> criteria = List.of(
                new SearchCriterion("CUNO", "Y11100"),
                new SearchCriterion("ORST", "33")
        );

        QueryContext context = assembler.assembleSearch("SearchCustomerOrder", slots, criteria);

        assertEquals("SearchCustomerOrder", context.intentName());
        assertEquals(slots, context.slots());
        assertEquals(criteria, context.criteria());
        assertTrue(context.requestedInformation().isEmpty());
        assertNull(context.limit());
        assertTrue(context.returnColumns().isEmpty());
        assertNull(context.positionKey());
        assertFalse(context.continuationRequested());
    }

    @Test
    void assembleRead_populatesIntentAndSlotsOnly() {
        QueryContext context = assembler.assembleRead(
                "GetCustomer",
                Map.of("CustomerNumber", "107685")
        );

        assertEquals("GetCustomer", context.intentName());
        assertEquals("107685", context.slots().get("CustomerNumber"));
        assertTrue(context.criteria().isEmpty());
    }
}
