package com.ai.openai_api_service.service;

import com.ai.openai_api_service.model.python_rag.ChunkItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAIServiceRagContextTest {

    private OpenAIService openAIService;

    @BeforeEach
    void setUp() {
        openAIService = new OpenAIService(null, null, null);
    }

    @Test
    void formatRagContext_includesTitleSectionProgramsAndSource() {
        ChunkItem chunk = new ChunkItem();
        chunk.setChunk("Configure customer order entry.");
        chunk.setScore(0.62f);
        chunk.setTitle("Customer Order Configuration");
        chunk.setSectionPath(List.of("Orders", "Creating Orders"));
        chunk.setProgramIds(List.of("OIS300"));
        chunk.setSource("https://docs.example/ois300");

        String formatted = openAIService.formatRagContext(List.of(chunk));

        assertTrue(formatted.contains("Title: Customer Order Configuration"));
        assertTrue(formatted.contains("Section: Orders > Creating Orders"));
        assertTrue(formatted.contains("Programs: OIS300"));
        assertTrue(formatted.contains("Source: https://docs.example/ois300"));
        assertTrue(formatted.contains("Configure customer order entry."));
    }
}
