package com.ai.openai_api_service.service;

import com.ai.openai_api_service.exception.OpenAIException;
import com.ai.openai_api_service.model.lex.LexRecognizeResult;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LexIntentMapperTest {

    private final LexIntentMapper mapper = new LexIntentMapper();

    @Test
    void mapGetCustomer_mapsSlotToM3Request() {
        LexRecognizeResult lexResult = new LexRecognizeResult(
                "GetCustomer",
                "ReadyForFulfillment",
                "Close",
                null,
                Map.of("CustomerNumber", "CSU001"),
                java.util.List.of()
        );

        Optional<LexIntentMapper.MappedM3Request> mapped = mapper.map(lexResult);

        assertTrue(mapped.isPresent());
        assertEquals("CRS610MI", mapped.get().program());
        assertEquals("GetBasicData", mapped.get().transaction());
        assertEquals("CSU001", mapped.get().params().get("CUNO"));
        assertEquals("read", mapped.get().actionTaken());
    }

    @Test
    void mapGetCustomer_normalizesCunoToUpperCase() {
        LexRecognizeResult lexResult = new LexRecognizeResult(
                "GetCustomer",
                "ReadyForFulfillment",
                "Close",
                null,
                Map.of("CustomerNumber", "y11300"),
                java.util.List.of()
        );

        Optional<LexIntentMapper.MappedM3Request> mapped = mapper.map(lexResult);

        assertTrue(mapped.isPresent());
        assertEquals("Y11300", mapped.get().params().get("CUNO"));
    }

    @Test
    void mapGetCustomer_stripsTrailingNumberLabel() {
        LexRecognizeResult lexResult = new LexRecognizeResult(
                "GetCustomer",
                "ReadyForFulfillment",
                "Close",
                null,
                Map.of("CustomerNumber", "107685 number"),
                java.util.List.of()
        );

        Optional<LexIntentMapper.MappedM3Request> mapped = mapper.map(lexResult);

        assertTrue(mapped.isPresent());
        assertEquals("107685", mapped.get().params().get("CUNO"));
    }

    @Test
    void mapGetCustomer_invalidCuno_throwsInvalidLexSlotException() {
        LexRecognizeResult lexResult = new LexRecognizeResult(
                "GetCustomer",
                "ReadyForFulfillment",
                "Close",
                null,
                Map.of("CustomerNumber", "not-a-valid-cuno"),
                java.util.List.of()
        );

        assertThrows(
                com.ai.openai_api_service.exception.InvalidLexSlotException.class,
                () -> mapper.map(lexResult)
        );
    }

    @Test
    void mapGetCustomer_missingSlot_throws() {
        LexRecognizeResult lexResult = new LexRecognizeResult(
                "GetCustomer",
                "ReadyForFulfillment",
                "Close",
                null,
                Map.of(),
                java.util.List.of()
        );

        assertThrows(OpenAIException.class, () -> mapper.map(lexResult));
    }

    @Test
    void mapUnknownIntent_returnsEmpty() {
        LexRecognizeResult lexResult = new LexRecognizeResult(
                "UnknownIntent",
                "ReadyForFulfillment",
                "Close",
                null,
                Map.of(),
                java.util.List.of()
        );

        assertTrue(mapper.map(lexResult).isEmpty());
    }
}
