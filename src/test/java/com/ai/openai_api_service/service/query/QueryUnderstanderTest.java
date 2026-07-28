package com.ai.openai_api_service.service.query;

import com.ai.openai_api_service.model.QueryContext;
import com.ai.openai_api_service.model.SearchCriterion;
import com.ai.openai_api_service.service.IntentApiCatalog;
import com.ai.openai_api_service.service.RequestedInformationResolver;
import com.ai.openai_api_service.service.api.ApiFieldCatalog;
import com.ai.openai_api_service.service.api.InformationRequestCatalog;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryUnderstanderTest {

    private final QueryUnderstander understander = new QueryUnderstander(
            new RequestedInformationResolver(
                    new com.ai.openai_api_service.service.SearchFieldCatalog(),
                    new InformationRequestCatalog()
            ),
            new IntentApiCatalog(),
            new ReturnColumnCatalog(new IntentApiCatalog(), new ApiFieldCatalog())
    );

    @Test
    void parseLimit_extractsLastN() {
        assertEquals(5, QueryUnderstander.parseLimit("show last 5 customer orders"));
        assertEquals(10, QueryUnderstander.parseLimit("top 10 purchase orders"));
        assertNull(QueryUnderstander.parseLimit("show customer orders"));
    }

    @Test
    void parseContinuation_detectsShowMore() {
        assertTrue(QueryUnderstander.parseContinuation("show more"));
        assertFalse(QueryUnderstander.parseContinuation("show customer Y11100"));
    }

    @Test
    void enrich_search_addsLimitWithoutChangingCriteria() {
        QueryContext base = QueryContext.forSearch(
                "SearchCustomerOrder",
                Map.of("CustomerNumber", "Y11100"),
                List.of(new SearchCriterion("CUNO", "Y11100"))
        );

        QueryContext enriched = understander.enrich(
                base,
                "show last 5 customer orders for Y11100",
                Map.of()
        );

        assertEquals(5, enriched.limit());
        assertEquals(base.criteria(), enriched.criteria());
    }

    @Test
    void enrich_getCustomerFinancial_paymentAndCurrency_addsReturnColumns() {
        QueryContext base = QueryContext.forRead(
                "GetCustomerFinancial",
                Map.of("CustomerNumber", "Y11100")
        );

        QueryContext enriched = understander.enrich(
                base,
                "Show payment and currency for customer Y11100",
                Map.of()
        );

        assertEquals(
                java.util.Set.of("TEPY", "CUCD"),
                java.util.Set.copyOf(enriched.returnColumns())
        );
        assertEquals(
                List.of(RequestedInformationResolver.PAYMENT, RequestedInformationResolver.CURRENCY),
                enriched.requestedInformation()
        );
    }
}
