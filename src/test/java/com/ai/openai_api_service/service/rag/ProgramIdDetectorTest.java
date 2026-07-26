package com.ai.openai_api_service.service.rag;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgramIdDetectorTest {

    @Test
    void detect_findsCommonProgramIds() {
        assertEquals(
                List.of("OIS300", "CRS610", "MMS200"),
                ProgramIdDetector.detect("Explain OIS300 and CRS610 with MMS200")
        );
    }

    @Test
    void detect_handlesPanelSuffix() {
        assertEquals(List.of("OIS100"), ProgramIdDetector.detect("open OIS100/E panel"));
    }

    @Test
    void detect_unionsAcrossTextsAndDedupes() {
        assertEquals(
                List.of("PPS220", "OIS680"),
                ProgramIdDetector.detect("print in PPS220", "also OIS680 and pps220")
        );
    }

    @Test
    void detect_emptyWhenNoMatch() {
        assertTrue(ProgramIdDetector.detect("how does pricing work").isEmpty());
    }
}
