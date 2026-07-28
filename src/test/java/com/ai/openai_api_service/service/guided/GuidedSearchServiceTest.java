package com.ai.openai_api_service.service.guided;

import com.ai.openai_api_service.model.ChatResponse;
import com.ai.openai_api_service.model.GuidedSearchPhase;
import com.ai.openai_api_service.model.GuidedSearchState;
import com.ai.openai_api_service.model.LexFulfillmentOutcome;
import com.ai.openai_api_service.model.LexFulfillmentSession;
import com.ai.openai_api_service.model.M3RequestDto;
import com.ai.openai_api_service.model.SearchCriterion;
import com.ai.openai_api_service.service.IntentApiCatalog;
import com.ai.openai_api_service.service.LexFulfillmentService;
import com.ai.openai_api_service.service.SearchFieldCatalog;
import com.ai.openai_api_service.service.normalizer.FieldDefinitionRegistry;
import com.ai.openai_api_service.service.normalizer.SlotNormalizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GuidedSearchServiceTest {

    @Mock
    private LexFulfillmentService lexFulfillmentService;

    private InMemoryGuidedSearchSessionService sessionService;
    private GuidedSearchService guidedSearchService;
    private LexFulfillmentSession session;

    @BeforeEach
    void setUp() {
        sessionService = new InMemoryGuidedSearchSessionService(3600);
        guidedSearchService = new GuidedSearchService(
                new SearchFieldCatalog(),
                new IntentApiCatalog(),
                new SlotNormalizer(new SearchFieldCatalog(), new FieldDefinitionRegistry()),
                new FieldDefinitionRegistry(),
                lexFulfillmentService,
                sessionService
        );
        session = LexFulfillmentSession.of("infor", "user1", "session-guided");
    }

    @Test
    void start_customerOrder_showsMetadataMenuAndStoresSelectFieldState() {
        ChatResponse response = guidedSearchService.start("SearchCustomerOrder", session);

        assertEquals(GuidedSearchService.ACTION_SELECT_FIELD, response.getActionTaken());
        assertEquals("SearchCustomerOrder", response.getCollectingTool());
        assertTrue(response.getReply().contains("Please select a search field"));
        assertTrue(response.getReply().contains("1. Customer Order Number"));
        assertTrue(response.getReply().contains("11. Payer"));
        assertTrue(response.getReply().contains("Requested Delivery Date"));
        assertTrue(response.getReply().contains("Customer Number"));
        assertTrue(response.getReply().toLowerCase().contains("cancel"));
        assertNotNull(sessionService.find(session).orElse(null));
        assertEquals(GuidedSearchPhase.SELECT_FIELD, sessionService.find(session).get().phase());
    }

    @Test
    void handleTurn_selectFieldByNumber_movesToCollectValue() {
        guidedSearchService.start("SearchCustomerOrder", session);
        GuidedSearchState state = sessionService.find(session).orElseThrow();

        GuidedSearchService.GuidedTurnResult result = guidedSearchService.handleTurn(session, state, "6");

        assertFalse(result.abandonToLex());
        assertEquals(GuidedSearchService.ACTION_COLLECT_VALUE, result.response().getActionTaken());
        assertTrue(result.response().getReply().contains("Highest Order Status"));
        assertTrue(result.response().getReply().contains("Example: 77"));
        assertEquals(GuidedSearchPhase.COLLECT_VALUE, sessionService.find(session).orElseThrow().phase());
    }

    @Test
    void handleTurn_selectFieldByAlias_movesToCollectValue() {
        guidedSearchService.start("SearchCustomerOrder", session);
        GuidedSearchState state = sessionService.find(session).orElseThrow();

        GuidedSearchService.GuidedTurnResult result =
                guidedSearchService.handleTurn(session, state, "highest status");

        assertFalse(result.abandonToLex());
        assertEquals(GuidedSearchService.ACTION_COLLECT_VALUE, result.response().getActionTaken());
        assertTrue(result.response().getReply().contains("Highest Order Status"));
    }

    @Test
    void handleTurn_unresolvedSelectDoesNotAbandonForOrdinaryInput() {
        guidedSearchService.start("SearchCustomerOrder", session);
        GuidedSearchState state = sessionService.find(session).orElseThrow();

        GuidedSearchService.GuidedTurnResult result = guidedSearchService.handleTurn(session, state, "something");

        assertFalse(result.abandonToLex());
        assertEquals(GuidedSearchService.ACTION_SELECT_FIELD, result.response().getActionTaken());
        assertTrue(result.response().getReply().contains("couldn't match"));
    }

    @Test
    void handleTurn_unresolvedSelectAbandonsOnNewSearchIntent() {
        guidedSearchService.start("SearchCustomerOrder", session);
        GuidedSearchState state = sessionService.find(session).orElseThrow();

        GuidedSearchService.GuidedTurnResult result =
                guidedSearchService.handleTurn(session, state, "actually search purchase orders");

        assertTrue(result.abandonToLex());
        assertTrue(sessionService.find(session).isEmpty());
    }

    @Test
    void handleTurn_collectValueValid_callsFulfillSearchAndClearsSession() {
        GuidedSearchState state = GuidedSearchState.collectValue(
                "SearchCustomerOrder",
                "ORST",
                "HighestStatus",
                Map.of()
        );
        sessionService.put(session, state);

        M3RequestDto m3Request = new M3RequestDto(true, "OIS100MI", "SearchHead", Map.of("SQRY", "ORST:'77'"));
        ChatResponse search = new ChatResponse("Processing...", false);
        search.setActionTaken("search");
        search.setM3Request(m3Request);
        when(lexFulfillmentService.fulfillSearch(
                eq("SearchCustomerOrder"),
                eq(Map.of("HighestStatus", "77")),
                eq("77"),
                any()
        )).thenReturn(new LexFulfillmentOutcome(search, List.of(new SearchCriterion("ORST", "77"))));

        GuidedSearchService.GuidedTurnResult result = guidedSearchService.handleTurn(session, state, "77");

        assertFalse(result.abandonToLex());
        assertEquals("search", result.response().getActionTaken());
        verify(lexFulfillmentService).fulfillSearch(
                eq("SearchCustomerOrder"),
                eq(Map.of("HighestStatus", "77")),
                eq("77"),
                any()
        );
        assertTrue(sessionService.find(session).isEmpty());
    }

    @Test
    void handleTurn_collectValueInvalid_repromptsInsteadOfAbandon() {
        GuidedSearchState state = GuidedSearchState.collectValue(
                "SearchCustomerOrder",
                "ORST",
                "HighestStatus",
                Map.of()
        );
        sessionService.put(session, state);

        GuidedSearchService.GuidedTurnResult result = guidedSearchService.handleTurn(session, state, "ABC");

        assertFalse(result.abandonToLex());
        assertEquals(GuidedSearchService.ACTION_COLLECT_VALUE, result.response().getActionTaken());
        assertTrue(result.response().getReply().contains("Invalid value"));
        assertTrue(sessionService.find(session).isPresent());
    }

    @Test
    void isCancel_recognizesCancelWords() {
        assertTrue(GuidedSearchService.isCancel("cancel"));
        assertFalse(GuidedSearchService.isCancel("customer orders"));
    }
}
