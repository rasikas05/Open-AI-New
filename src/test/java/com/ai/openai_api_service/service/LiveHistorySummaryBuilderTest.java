package com.ai.openai_api_service.service;

import com.ai.openai_api_service.model.ChatResponse;
import com.ai.openai_api_service.model.LiveHistoryAuditMetadata;
import com.ai.openai_api_service.model.LiveHistoryResult;
import com.ai.openai_api_service.model.M3RequestDto;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiveHistorySummaryBuilderTest {

    private final LiveHistorySummaryBuilder builder = new LiveHistorySummaryBuilder();

    @Test
    void build_getCustomerRead_returnsSummaryAndAuditMetadata() {
        M3RequestDto m3Request = new M3RequestDto(true, "CRS610MI", "GetBasicData", Map.of("CUNO", "C00A000002"));
        ChatResponse chatResponse = new ChatResponse("Looking up customer C00A000002...", false);
        chatResponse.setActionTaken("read");
        chatResponse.setLexIntent("GetCustomer");
        chatResponse.setM3Request(m3Request);

        LiveHistoryResult result = builder.build(chatResponse).orElseThrow();

        assertEquals(
                "Viewed customer C00A000002.\n\n" + LiveHistorySummaryBuilder.FOOTER,
                result.summaryText()
        );
        assertEquals(
                new LiveHistoryAuditMetadata("GetCustomer", "Customer", "C00A000002"),
                result.auditMetadata()
        );
    }

    @Test
    void build_nonLiveResponse_returnsEmpty() {
        ChatResponse chatResponse = new ChatResponse("grounded answer", false);
        chatResponse.setActionTaken("rag");

        assertTrue(builder.build(chatResponse).isEmpty());
    }

    @Test
    void build_getCustomerReadWithoutCuno_usesGenericHeadline() {
        M3RequestDto m3Request = new M3RequestDto(true, "CRS610MI", "GetBasicData", Map.of());
        ChatResponse chatResponse = new ChatResponse("Looking up customer...", false);
        chatResponse.setActionTaken("read");
        chatResponse.setLexIntent("GetCustomer");
        chatResponse.setM3Request(m3Request);

        LiveHistoryResult result = builder.build(chatResponse).orElseThrow();

        assertEquals("Viewed customer.\n\n" + LiveHistorySummaryBuilder.FOOTER, result.summaryText());
        assertEquals(new LiveHistoryAuditMetadata("GetCustomer", "Customer", null), result.auditMetadata());
    }
}
