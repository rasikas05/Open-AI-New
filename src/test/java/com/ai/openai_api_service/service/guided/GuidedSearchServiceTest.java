package com.ai.openai_api_service.service.guided;

import com.ai.openai_api_service.model.ChatResponse;
import com.ai.openai_api_service.model.GuidedSearchPhase;
import com.ai.openai_api_service.model.GuidedSearchState;
import com.ai.openai_api_service.model.LexFulfillmentSession;
import com.ai.openai_api_service.service.IntentApiCatalog;
import com.ai.openai_api_service.service.SearchFieldCatalog;
import com.ai.openai_api_service.service.normalizer.FieldDefinitionRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuidedSearchServiceTest {

    private InMemoryGuidedSearchSessionService sessionService;
    private GuidedSearchService guidedSearchService;
    private LexFulfillmentSession session;

    @BeforeEach
    void setUp() {
        sessionService = new InMemoryGuidedSearchSessionService(3600);
        guidedSearchService = new GuidedSearchService(
                new SearchFieldCatalog(),
                new IntentApiCatalog(),
                new FieldDefinitionRegistry(),
                sessionService
        );
        session = LexFulfillmentSession.of("infor", "user1", "session-guided");
    }

    @Test
    void start_buildsMenuFromCatalog() {
        ChatResponse response = guidedSearchService.start("SearchCustomerOrder", session);

        assertEquals(GuidedSearchService.ACTION_SELECT_FIELD, response.getActionTaken());
        assertEquals("SearchCustomerOrder", response.getCollectingTool());
        assertEquals(GuidedSearchService.NEXT_FIELD_CHOICE, response.getNextField());
        assertTrue(response.getReply().contains("How would you like to search?"));
        assertTrue(response.getReply().contains("Customer Number"));
        assertTrue(response.getReply().contains("Customer Order Number"));
        assertTrue(response.getReply().toLowerCase().contains("cancel"));
        assertNotNull(response.getCollectedArgs());
        assertTrue(sessionService.find(session).isPresent());
        assertEquals(GuidedSearchPhase.SELECT_FIELD, sessionService.find(session).get().phase());
    }

    @Test
    void start_purchaseOrder_buildsMenuFromCatalog() {
        ChatResponse response = guidedSearchService.start("SearchPurchaseOrder", session);

        assertEquals(GuidedSearchService.ACTION_SELECT_FIELD, response.getActionTaken());
        assertTrue(response.getReply().contains("Supplier"));
        assertTrue(response.getReply().contains("Warehouse"));
    }

    @Test
    void handleTurn_selectFieldByIndex_movesToCollectValue() {
        guidedSearchService.start("SearchCustomerOrder", session);
        GuidedSearchState state = sessionService.find(session).orElseThrow();

        GuidedSearchService.GuidedTurnResult result =
                guidedSearchService.handleTurn(session, state, "1");

        assertFalse(result.shouldResumeFulfillment());
        assertEquals(GuidedSearchService.ACTION_COLLECT_VALUE, result.response().getActionTaken());
        assertEquals("ORNO", result.response().getNextField());
        assertTrue(result.response().getReply().toLowerCase().contains("customer order number"));
        GuidedSearchState updated = sessionService.find(session).orElseThrow();
        assertEquals(GuidedSearchPhase.COLLECT_VALUE, updated.phase());
        assertEquals("ORNO", updated.selectedM3Field());
        assertEquals("CustomerOrderNumber", updated.selectedLexSlot());
    }

    @Test
    void handleTurn_collectValidValue_resumesWithSlots() {
        sessionService.put(session, GuidedSearchState.collectValue(
                "SearchCustomerOrder", "ORNO", "CustomerOrderNumber"));
        GuidedSearchState state = sessionService.find(session).orElseThrow();

        GuidedSearchService.GuidedTurnResult result =
                guidedSearchService.handleTurn(session, state, "1000001234");

        assertTrue(result.shouldResumeFulfillment());
        assertEquals("SearchCustomerOrder", result.intentName());
        assertEquals(Map.of("CustomerOrderNumber", "1000001234"), result.slots());
        assertTrue(sessionService.find(session).isEmpty());
    }

    @Test
    void handleTurn_collectInvalidValue_keepsSessionAndHintsCancel() {
        sessionService.put(session, GuidedSearchState.collectValue(
                "SearchCustomerOrder", "ORNO", "CustomerOrderNumber"));
        GuidedSearchState state = sessionService.find(session).orElseThrow();

        GuidedSearchService.GuidedTurnResult result =
                guidedSearchService.handleTurn(session, state, "ab");

        assertFalse(result.shouldResumeFulfillment());
        assertEquals(GuidedSearchService.ACTION_COLLECT_VALUE, result.response().getActionTaken());
        assertTrue(result.response().getReply().toLowerCase().contains("cancel"));
        assertTrue(sessionService.find(session).isPresent());
    }

    @Test
    void handleTurn_cancel_clearsSession() {
        guidedSearchService.start("SearchCustomerOrder", session);
        GuidedSearchState state = sessionService.find(session).orElseThrow();

        GuidedSearchService.GuidedTurnResult result =
                guidedSearchService.handleTurn(session, state, "cancel");

        assertEquals(GuidedSearchService.ACTION_CANCELLED, result.response().getActionTaken());
        assertTrue(sessionService.find(session).isEmpty());
    }

    @Test
    void humanizeIntent_searchCustomerOrder() {
        assertEquals("customer order", GuidedSearchService.humanizeIntent("SearchCustomerOrder"));
    }
}
