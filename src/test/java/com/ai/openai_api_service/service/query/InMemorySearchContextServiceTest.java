package com.ai.openai_api_service.service.query;

import com.ai.openai_api_service.model.LexFulfillmentSession;
import com.ai.openai_api_service.model.M3ClientReportDto;
import com.ai.openai_api_service.model.QueryContext;
import com.ai.openai_api_service.service.IntentApiCatalog;
import com.ai.openai_api_service.service.LexIntentMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemorySearchContextServiceTest {

    private final InMemorySearchContextService service =
            new InMemorySearchContextService(new IntentApiCatalog(), 3600);

    private static final LexFulfillmentSession SESSION =
            LexFulfillmentSession.of("infor", "user1", "session-1");

    @Test
    void startOrReplaceSearch_andClientReport_enablesContinuation() {
        LexIntentMapper.MappedM3Request mapped = new LexIntentMapper.MappedM3Request(
                "OIS100MI",
                "SearchHead",
                Map.of("SQRY", "CUNO:Y11100"),
                "search"
        );
        QueryContext queryContext = QueryContext.forSearch(
                "SearchCustomerOrder",
                Map.of(),
                java.util.List.of()
        );

        var started = service.startOrReplaceSearch(
                SESSION,
                "SearchCustomerOrder",
                mapped,
                queryContext
        );
        assertTrue(started != null);

        M3ClientReportDto report = new M3ClientReportDto();
        report.setSearchContextId(started.searchContextId());
        report.setPositionkey("cursor-abc");
        service.applyClientReport(SESSION, report);

        QueryContext continuationBase = QueryContext.forSearch(
                "SearchCustomerOrder",
                Map.of(),
                java.util.List.of()
        ).withContinuationRequested(true);

        Optional<LexIntentMapper.MappedM3Request> next =
                service.buildContinuationRequest(SESSION, continuationBase);

        assertTrue(next.isPresent());
        assertEquals("cursor-abc", next.get().params().get("positionkey"));
        assertEquals("CUNO:Y11100", next.get().params().get("SQRY"));
    }

    @Test
    void clearSession_removesActiveContext() {
        LexIntentMapper.MappedM3Request mapped = new LexIntentMapper.MappedM3Request(
                "OIS100MI",
                "SearchHead",
                Map.of("SQRY", "CUNO:Y11100"),
                "search"
        );
        service.startOrReplaceSearch(
                SESSION,
                "SearchCustomerOrder",
                mapped,
                QueryContext.forSearch("SearchCustomerOrder", Map.of(), java.util.List.of())
        );

        service.clearSession(SESSION);

        assertTrue(service.findActive(SESSION).isEmpty());
    }
}
