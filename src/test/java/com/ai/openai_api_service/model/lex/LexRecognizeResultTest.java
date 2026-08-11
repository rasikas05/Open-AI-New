package com.ai.openai_api_service.service;

import com.ai.openai_api_service.model.lex.LexRecognizeResult;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.lexruntimev2.model.DialogAction;
import software.amazon.awssdk.services.lexruntimev2.model.Intent;
import software.amazon.awssdk.services.lexruntimev2.model.Message;
import software.amazon.awssdk.services.lexruntimev2.model.RecognizeTextResponse;
import software.amazon.awssdk.services.lexruntimev2.model.SessionState;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LexRecognizeResultTest {

    @Test
    void fromResponse_elicitSlot() {
        RecognizeTextResponse response = RecognizeTextResponse.builder()
                .sessionState(SessionState.builder()
                        .intent(Intent.builder()
                                .name("GetCustomer")
                                .state("InProgress")
                                .build())
                        .dialogAction(DialogAction.builder()
                                .type("ElicitSlot")
                                .slotToElicit("CustomerNumber")
                                .build())
                        .build())
                .messages(List.of(Message.builder().content("What is the customer number?").build()))
                .build();

        LexRecognizeResult result = LexRecognizeResult.fromResponse(response);

        assertEquals("GetCustomer", result.getIntentName());
        assertTrue(result.isElicitSlot());
        assertFalse(result.isReadyForFulfillment());
        assertFalse(result.isFallbackIntent());
        assertEquals("CustomerNumber", result.getSlotToElicit());
        assertEquals("What is the customer number?", result.firstMessage());
    }

    @Test
    void fromResponse_elicitIntent() {
        RecognizeTextResponse response = RecognizeTextResponse.builder()
                .sessionState(SessionState.builder()
                        .dialogAction(DialogAction.builder()
                                .type("ElicitIntent")
                                .build())
                        .build())
                .messages(List.of(Message.builder()
                        .content("Which of the following options did you mean? Search Purchase Order or Search Distribution Order")
                        .build()))
                .build();

        LexRecognizeResult result = LexRecognizeResult.fromResponse(response);

        assertEquals(null, result.getIntentName());
        assertTrue(result.isElicitIntent());
        assertFalse(result.isElicitSlot());
        assertFalse(result.isReadyForFulfillment());
        assertFalse(result.isFallbackIntent());
        assertEquals("ElicitIntent", result.getDialogActionType());
        assertEquals(
                "Which of the following options did you mean? Search Purchase Order or Search Distribution Order",
                result.firstMessage()
        );
    }

    @Test
    void readyForFulfillment_detectedByConstructor() {
        LexRecognizeResult result = new LexRecognizeResult(
                "GetCustomer",
                "ReadyForFulfillment",
                "Close",
                null,
                java.util.Map.of("CustomerNumber", "CSU001"),
                List.of()
        );

        assertTrue(result.isReadyForFulfillment());
        assertEquals("CSU001", result.getSlots().get("CustomerNumber"));
    }

    @Test
    void fromResponse_fallbackIntent() {
        RecognizeTextResponse response = RecognizeTextResponse.builder()
                .sessionState(SessionState.builder()
                        .intent(Intent.builder()
                                .name("FallbackIntent")
                                .state("Fulfilled")
                                .build())
                        .build())
                .build();

        LexRecognizeResult result = LexRecognizeResult.fromResponse(response);

        assertTrue(result.isFallbackIntent());
    }

    @Test
    void fromResponse_parsesSessionAttributes() {
        RecognizeTextResponse response = RecognizeTextResponse.builder()
                .sessionState(SessionState.builder()
                        .intent(Intent.builder()
                                .name("GetCustomer")
                                .state("InProgress")
                                .build())
                        .sessionAttributes(Map.of(
                                LexRecognizeResult.ATTR_REQUESTED_INFORMATION,
                                "ADDRESS"
                        ))
                        .build())
                .build();

        LexRecognizeResult result = LexRecognizeResult.fromResponse(response);

        assertEquals(
                "ADDRESS",
                result.getSessionAttributes().get(LexRecognizeResult.ATTR_REQUESTED_INFORMATION)
        );
    }
}
