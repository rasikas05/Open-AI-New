package com.ai.openai_api_service.service;

import com.ai.openai_api_service.exception.OpenAIException;
import com.ai.openai_api_service.model.ChatResponse;
import com.ai.openai_api_service.model.lex.LexRecognizeResult;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LexFulfillmentServiceTest {

    private final LexFulfillmentService fulfillmentService =
            new LexFulfillmentService(new LexIntentMapper(new IntentApiCatalog()));

    @Test
    void fulfill_buildsM3RequestWithoutCallingPython() {
        LexRecognizeResult lexResult = new LexRecognizeResult(
                "GetCustomer",
                "ReadyForFulfillment",
                "Close",
                null,
                Map.of("CustomerNumber", "107685"),
                java.util.List.of()
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
                java.util.List.of()
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
                java.util.List.of()
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
                java.util.List.of()
        );

        assertThrows(OpenAIException.class, () -> fulfillmentService.fulfill(lexResult));
    }
}
