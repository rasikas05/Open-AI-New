package com.ai.openai_api_service.service;

import com.ai.openai_api_service.model.lex.LexRecognizeResult;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.lexruntimev2.model.DialogAction;
import software.amazon.awssdk.services.lexruntimev2.model.Intent;
import software.amazon.awssdk.services.lexruntimev2.model.Message;
import software.amazon.awssdk.services.lexruntimev2.model.RecognizeTextResponse;
import software.amazon.awssdk.services.lexruntimev2.model.SessionState;

import java.util.List;

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
}
