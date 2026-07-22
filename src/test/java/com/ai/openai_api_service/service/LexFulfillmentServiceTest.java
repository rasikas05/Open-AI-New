package com.ai.openai_api_service.service;

import com.ai.openai_api_service.exception.OpenAIException;
import com.ai.openai_api_service.model.LexFulfillmentOutcome;
import com.ai.openai_api_service.model.ChatResponse;
import com.ai.openai_api_service.model.lex.LexRecognizeResult;
import com.ai.openai_api_service.service.api.ApiCapabilityResolver;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LexFulfillmentServiceTest {

    private final LexFulfillmentService fulfillmentService =
            LexFulfillmentServiceTestSupport.createFulfillmentService();

    @Test
    void fulfill_buildsM3RequestWithoutCallingPython() {
        LexRecognizeResult lexResult = new LexRecognizeResult(
                "GetCustomer",
                "ReadyForFulfillment",
                "Close",
                null,
                Map.of("CustomerNumber", "107685"),
                List.of()
        );

        ChatResponse response = fulfillmentService.fulfill(lexResult);

        assertEquals("Looking up customer 107685...", response.getReply());
        assertEquals("read", response.getActionTaken());
        assertEquals("GetCustomer", response.getLexIntent());
        assertNull(response.getM3Data());
        assertTrue(response.getM3Request().isExecute());
        assertEquals("CRS610MI", response.getM3Request().getProgram());
        assertEquals("GetBasicData", response.getM3Request().getTransaction());
        assertEquals("107685", response.getM3Request().getParams().get("CUNO"));
    }

    @Test
    void fulfill_invalidCustomerNumber_returnsFriendlyMessageWithoutM3Request() {
        LexRecognizeResult lexResult = new LexRecognizeResult(
                "GetCustomer",
                "ReadyForFulfillment",
                "Close",
                null,
                Map.of("CustomerNumber", "107685-NUMBER"),
                List.of()
        );

        ChatResponse response = fulfillmentService.fulfill(lexResult);

        assertEquals("lex_invalid_slot", response.getActionTaken());
        assertEquals("GetCustomer", response.getLexIntent());
        assertFalse(response.getReply().contains("Looking up customer"));
        assertTrue(response.getReply().contains("valid customer number"));
        assertNull(response.getM3Request());
    }

    @Test
    void fulfill_stripsTrailingNumberLabelBeforeM3Request() {
        LexRecognizeResult lexResult = new LexRecognizeResult(
                "GetCustomer",
                "ReadyForFulfillment",
                "Close",
                null,
                Map.of("CustomerNumber", "107685 number"),
                List.of()
        );

        ChatResponse response = fulfillmentService.fulfill(lexResult);

        assertEquals("Looking up customer 107685...", response.getReply());
        assertEquals("107685", response.getM3Request().getParams().get("CUNO"));
    }

    @Test
    void fulfill_unmappedIntent_throws() {
        LexRecognizeResult lexResult = new LexRecognizeResult(
                "UnknownIntent",
                "ReadyForFulfillment",
                "Close",
                null,
                Map.of(),
                List.of()
        );

        assertThrows(OpenAIException.class, () -> fulfillmentService.fulfill(lexResult));
    }

    @Test
    void fulfill_searchCustomerOrder_usesGenericPipeline() {
        Map<String, String> slots = new LinkedHashMap<>();
        slots.put("CustomerNumber", "C00001");
        slots.put("Facility", "A01");
        slots.put("Status", "33");
        slots.put("OrderDate", "2026-04-24");

        LexRecognizeResult lexResult = new LexRecognizeResult(
                "SearchCustomerOrder",
                "ReadyForFulfillment",
                "Close",
                null,
                slots,
                List.of()
        );

        ChatResponse response = fulfillmentService.fulfill(lexResult);

        assertEquals("search", response.getActionTaken());
        assertEquals("SearchCustomerOrder", response.getLexIntent());
        assertEquals("Processing your request...", response.getReply());
        assertNull(response.getM3Data());
        assertTrue(response.getM3Request().isExecute());
        assertEquals("OIS100MI", response.getM3Request().getProgram());
        assertEquals("SearchHead", response.getM3Request().getTransaction());
        assertEquals(
                java.util.Set.of("CUNO:C00001", "FACI:A01", "ORST:33", "ORDT:20260424"),
                java.util.Set.of(response.getM3Request().getParams().get("SQRY").toString().split(" AND "))
        );
    }

    @Test
    void fulfill_searchPurchaseOrder_usesGenericPipeline() {
        Map<String, String> slots = new LinkedHashMap<>();
        slots.put("Supplier", "S00001");
        slots.put("Warehouse", "A01");
        slots.put("Status", "33");
        slots.put("OrderDate", "2026-04-24");

        LexRecognizeResult lexResult = new LexRecognizeResult(
                "SearchPurchaseOrder",
                "ReadyForFulfillment",
                "Close",
                null,
                slots,
                List.of()
        );

        ChatResponse response = fulfillmentService.fulfill(lexResult);

        assertEquals("search", response.getActionTaken());
        assertEquals("SearchPurchaseOrder", response.getLexIntent());
        assertNull(response.getM3Data());
        assertEquals("PPS200MI", response.getM3Request().getProgram());
        assertEquals("SearchHead", response.getM3Request().getTransaction());
        assertEquals(
                java.util.Set.of("SUNO:S00001", "WHLO:A01", "PUST:33", "PUDT:20260424"),
                java.util.Set.of(response.getM3Request().getParams().get("SQRY").toString().split(" AND "))
        );
    }

    @Test
    void fulfill_searchIntent_emptySlots_omitsSqry() {
        LexRecognizeResult lexResult = new LexRecognizeResult(
                "SearchCustomerOrder",
                "ReadyForFulfillment",
                "Close",
                null,
                Map.of(),
                List.of()
        );

        ChatResponse response = fulfillmentService.fulfill(lexResult);

        assertEquals("search", response.getActionTaken());
        assertEquals("OIS100MI", response.getM3Request().getProgram());
        assertEquals("SearchHead", response.getM3Request().getTransaction());
        assertTrue(response.getM3Request().getParams().isEmpty());
        assertFalse(response.getM3Request().getParams().containsKey("SQRY"));
        assertNull(response.getM3Data());
    }

    @Test
    void fulfillOutcome_searchCustomerOrder_returnsSearchCriteria() {
        Map<String, String> slots = new LinkedHashMap<>();
        slots.put("CustomerNumber", "C00001");
        slots.put("Facility", "A01");
        slots.put("Status", "33");

        LexRecognizeResult lexResult = new LexRecognizeResult(
                "SearchCustomerOrder",
                "ReadyForFulfillment",
                "Close",
                null,
                slots,
                List.of()
        );

        LexFulfillmentOutcome outcome = fulfillmentService.fulfillOutcome(lexResult, null);

        assertEquals("search", outcome.response().getActionTaken());
        assertFalse(outcome.searchCriteria().isEmpty());
        assertTrue(outcome.searchCriteria().stream().anyMatch(c -> "ORST".equals(c.field())));
        assertTrue(outcome.searchCriteria().stream().anyMatch(c -> "CUNO".equals(c.field())));
    }

    @Test
    void fulfill_searchCustomerOrder_mergedStatusWithoutOrderContext_omitsOrnoFromSqry() {
        Map<String, String> slots = new LinkedHashMap<>();
        slots.put("CustomerNumber", "Y11100");
        slots.put("Facility", "A01");
        slots.put("Status", "3320250433");

        String utterance =
                "Retrieve customer orders for customer Y11100 in facility A01 with status 33 on 2025-04-24";

        LexRecognizeResult lexResult = new LexRecognizeResult(
                "SearchCustomerOrder",
                "ReadyForFulfillment",
                "Close",
                null,
                slots,
                List.of()
        );

        ChatResponse response = fulfillmentService.fulfill(lexResult, utterance);

        assertEquals("search", response.getActionTaken());
        assertNull(response.getM3Data());
        String sqry = response.getM3Request().getParams().get("SQRY").toString();
        assertFalse(sqry.contains("ORNO:"));
        assertEquals(
                java.util.Set.of("CUNO:Y11100", "FACI:A01", "ORST:33"),
                java.util.Set.of(sqry.split(" AND "))
        );
    }

    @Test
    void fulfill_searchCustomerOrder_invalidMergedOrder_repairFromUtterance_sqryOrnoOnly() {
        Map<String, String> slots = new LinkedHashMap<>();
        slots.put("CustomerOrderNumber", "1000001234status");

        String utterance = "Show customer order 1000001234 status";

        LexRecognizeResult lexResult = new LexRecognizeResult(
                "SearchCustomerOrder",
                "ReadyForFulfillment",
                "Close",
                null,
                slots,
                List.of()
        );

        ChatResponse response = fulfillmentService.fulfill(lexResult, utterance);

        assertEquals("search", response.getActionTaken());
        String sqry = response.getM3Request().getParams().get("SQRY").toString();
        assertEquals("ORNO:1000001234", sqry);
        assertFalse(sqry.contains("CUNO:"));
    }

    @Test
    void fulfill_searchWithSession_returnsSearchContextId() {
        Map<String, String> slots = new LinkedHashMap<>();
        slots.put("CustomerNumber", "C00001");
        slots.put("Facility", "A01");
        slots.put("Status", "33");

        LexRecognizeResult lexResult = new LexRecognizeResult(
                "SearchCustomerOrder",
                "ReadyForFulfillment",
                "Close",
                null,
                slots,
                List.of()
        );

        LexFulfillmentOutcome outcome = fulfillmentService.fulfillOutcome(
                lexResult,
                null,
                com.ai.openai_api_service.model.LexFulfillmentSession.of("infor", "u1", "s1")
        );

        assertNotNull(outcome.response().getSearchContextId());
        assertNotNull(outcome.response().getPagination());
        assertTrue(outcome.response().getPagination().getSupportsContinuation());
    }

    @Test
    void fulfill_getCustomer_salespersonRequest_blocksM3WithMessage() {
        LexRecognizeResult lexResult = new LexRecognizeResult(
                "GetCustomer",
                "ReadyForFulfillment",
                "Close",
                null,
                Map.of("CustomerNumber", "107685"),
                List.of()
        );

        ChatResponse response = fulfillmentService.fulfill(
                lexResult,
                "Show salesperson for customer 107685"
        );

        assertEquals(ApiCapabilityResolver.ACTION_INFORMATION_NOT_AVAILABLE, response.getActionTaken());
        assertNull(response.getM3Request());
        assertTrue(response.getReply().contains("salesperson"));
    }

    @Test
    void fulfill_getCustomer_phoneRequest_addsReturncols() {
        LexRecognizeResult lexResult = new LexRecognizeResult(
                "GetCustomer",
                "ReadyForFulfillment",
                "Close",
                null,
                Map.of("CustomerNumber", "107685"),
                List.of()
        );

        ChatResponse response = fulfillmentService.fulfill(
                lexResult,
                "Show phone number for customer 107685"
        );

        assertEquals("read", response.getActionTaken());
        assertEquals("PHNO", response.getM3Request().getParams().get("returncols"));
    }

    @Test
    void fulfill_searchCustomerOrder_salespersonRequest_addsReturncols() {
        Map<String, String> slots = new LinkedHashMap<>();
        slots.put("CustomerNumber", "C00001");

        LexRecognizeResult lexResult = new LexRecognizeResult(
                "SearchCustomerOrder",
                "ReadyForFulfillment",
                "Close",
                null,
                slots,
                List.of()
        );

        ChatResponse response = fulfillmentService.fulfill(
                lexResult,
                "Show salesperson for customer C00001 orders"
        );

        assertEquals("search", response.getActionTaken());
        assertEquals("ORNO,SMCD", response.getM3Request().getParams().get("returncols"));
    }

    @Test
    void fulfill_searchCustomerOrder_orderStatusForCustomerLastFive_noSpuriousOrnoInSqry() {
        Map<String, String> slots = new LinkedHashMap<>();
        slots.put("CustomerNumber", "Y11100");
        slots.put("OrderDate", "5");

        LexRecognizeResult lexResult = new LexRecognizeResult(
                "SearchCustomerOrder",
                "ReadyForFulfillment",
                "Close",
                null,
                slots,
                List.of()
        );

        ChatResponse response = fulfillmentService.fulfill(
                lexResult,
                "Show order status for customer Y11100 last 5 orders"
        );

        assertEquals("search", response.getActionTaken());
        assertEquals(5, response.getM3Request().getParams().get("maxrecs"));
        String sqry = response.getM3Request().getParams().get("SQRY").toString();
        assertEquals("CUNO:Y11100", sqry);
        assertFalse(sqry.contains("ORNO:"));
        assertEquals("ORNO,ORST", response.getM3Request().getParams().get("returncols"));
    }

    @Test
    void fulfill_getCustomer_phoneAndEmailRequest_addsBothReturncols() {
        LexRecognizeResult lexResult = new LexRecognizeResult(
                "GetCustomer",
                "ReadyForFulfillment",
                "Close",
                null,
                Map.of("CustomerNumber", "107685"),
                List.of()
        );

        ChatResponse response = fulfillmentService.fulfill(
                lexResult,
                "Show phone and email for customer 107685"
        );

        assertEquals("read", response.getActionTaken());
        assertEquals("PHNO,MAIL", response.getM3Request().getParams().get("returncols"));
    }

    @Test
    void fulfill_searchCustomerOrder_statusAndAmount_mergesReturncols() {
        Map<String, String> slots = new LinkedHashMap<>();
        slots.put("CustomerNumber", "Y11100");

        LexRecognizeResult lexResult = new LexRecognizeResult(
                "SearchCustomerOrder",
                "ReadyForFulfillment",
                "Close",
                null,
                slots,
                List.of()
        );

        ChatResponse response = fulfillmentService.fulfill(
                lexResult,
                "Show order status and amount for customer Y11100"
        );

        assertEquals("search", response.getActionTaken());
        assertEquals("ORNO,ORST,NTAM", response.getM3Request().getParams().get("returncols"));
        assertEquals("CUNO:Y11100", response.getM3Request().getParams().get("SQRY").toString());
    }
}
