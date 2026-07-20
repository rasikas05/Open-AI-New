package com.ai.openai_api_service.service.m3;

import com.ai.openai_api_service.model.python_rag.M3MiCallResponse;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MiResponseParserTest {

    private final MiResponseParser parser = new MiResponseParser();

    @Test
    void parse_multiRow_extractsColumnsAndRows() {
        M3MiCallResponse response = new M3MiCallResponse(
                "PPS200MI",
                "SearchHead",
                null,
                null,
                List.of(
                        Map.of("PUNO", "100001", "SUNO", "S001"),
                        Map.of("PUNO", "100002", "PUST", "33")
                ),
                null
        );

        M3MiExecutionResult result = parser.parse(response);

        assertTrue(result.success());
        assertEquals(2, result.recordCount());
        assertEquals(List.of("PUNO", "PUST", "SUNO"), result.columns());
        assertEquals(2, result.rows().size());
        assertNull(result.errorMessage());
    }

    @Test
    void parse_emptyRecords_successWithZeroCount() {
        M3MiCallResponse response = new M3MiCallResponse(
                "OIS100MI",
                "SearchHead",
                null,
                null,
                List.of(),
                null
        );

        M3MiExecutionResult result = parser.parse(response);

        assertTrue(result.success());
        assertEquals(0, result.recordCount());
        assertTrue(result.rows().isEmpty());
    }

    @Test
    void parse_transportError_returnsFailure() {
        M3MiCallResponse response = new M3MiCallResponse(
                "OIS100MI",
                "SearchHead",
                null,
                null,
                List.of(),
                "Cannot connect to M3"
        );

        M3MiExecutionResult result = parser.parse(response);

        assertFalse(result.success());
        assertEquals("Cannot connect to M3", result.errorMessage());
        assertEquals(0, result.recordCount());
    }

    @Test
    void parse_rawJson_extractsRecords() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("results", List.of(Map.of(
                "errorMessage", "",
                "records", List.of(Map.of("ORNO", "0010000404", "CUNO", "C00AAA"))
        )));

        M3MiCallResponse response = new M3MiCallResponse(
                "OIS100MI",
                "SearchHead",
                raw,
                null,
                List.of(),
                null
        );

        M3MiExecutionResult result = parser.parse(response);

        assertTrue(result.success());
        assertEquals(1, result.recordCount());
        assertEquals("0010000404", result.rows().get(0).get("ORNO"));
    }

    @Test
    void parse_miErrorMessage_returnsFailure() {
        M3MiCallResponse response = new M3MiCallResponse(
                "OIS100MI",
                "SearchHead",
                null,
                "Invalid SQRY",
                List.of(),
                "Invalid SQRY"
        );

        M3MiExecutionResult result = parser.parse(response);

        assertFalse(result.success());
        assertEquals("Invalid SQRY", result.errorMessage());
    }
}
