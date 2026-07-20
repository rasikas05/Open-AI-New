package com.ai.openai_api_service.service.m3;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class M3SearchReplyFormatterTest {

    private final M3SearchReplyFormatter formatter = new M3SearchReplyFormatter();

    @Test
    void format_zeroRecords() {
        M3MiExecutionResult result = new M3MiExecutionResult(
                true, "OIS100MI", "SearchHead", 0, List.of(), List.of(), null
        );
        assertEquals("No records found.", formatter.format(result));
    }

    @Test
    void format_oneRecord() {
        M3MiExecutionResult result = new M3MiExecutionResult(
                true, "OIS100MI", "SearchHead", 1, List.of("ORNO"),
                List.of(Map.of("ORNO", "1")), null
        );
        assertEquals("Found 1 matching record.", formatter.format(result));
    }

    @Test
    void format_manyRecords() {
        M3MiExecutionResult result = new M3MiExecutionResult(
                true, "OIS100MI", "SearchHead", 12, List.of(), List.of(), null
        );
        assertEquals("Found 12 matching records.", formatter.format(result));
    }

    @Test
    void format_error() {
        M3MiExecutionResult result = M3MiExecutionResult.failure(
                "PPS200MI", "SearchHead", "timeout"
        );
        assertEquals("Search could not be completed: timeout", formatter.format(result));
    }
}
