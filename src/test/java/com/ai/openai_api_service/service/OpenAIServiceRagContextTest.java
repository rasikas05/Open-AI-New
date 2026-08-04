package com.ai.openai_api_service.service;

import com.ai.openai_api_service.model.python_rag.ChunkItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAIServiceRagContextTest {

    private OpenAIService openAIService;

    @BeforeEach
    void setUp() {
        openAIService = new OpenAIService(null, null, null);
    }

    @Test
    void formatRagContext_includesTitleSectionProgramsSourceAndRelevance() {
        ChunkItem chunk = new ChunkItem();
        chunk.setChunk("Configure customer order entry.");
        chunk.setScore(0.62f);
        chunk.setTitle("Customer Order Configuration");
        chunk.setSectionPath(List.of("Orders", "Creating Orders"));
        chunk.setProgramIds(List.of("OIS300"));
        chunk.setSource("https://docs.example/ois300");

        String formatted = openAIService.formatRagContext(List.of(chunk));

        assertTrue(formatted.contains("### Context"));
        assertTrue(formatted.contains("Title: Customer Order Configuration"));
        assertTrue(formatted.contains("Section: Orders > Creating Orders"));
        assertTrue(formatted.contains("Programs: OIS300"));
        assertTrue(formatted.contains("Source: https://docs.example/ois300"));
        assertTrue(formatted.contains("Relevance: 62.0%"));
        assertTrue(formatted.contains("Configure customer order entry."));
    }

    @Test
    void formatRagContext_hasNoDocumentNarrationLabels() {
        ChunkItem chunk1 = new ChunkItem();
        chunk1.setChunk("First chunk about registration.");
        chunk1.setScore(0.7f);
        chunk1.setTitle("Customer Registration");
        chunk1.setSource("https://docs.example/reg");

        ChunkItem chunk2 = new ChunkItem();
        chunk2.setChunk("Second chunk about registration.");
        chunk2.setScore(0.55f);
        chunk2.setTitle("Customer Master");
        chunk2.setSource("https://docs.example/master");

        String formatted = openAIService.formatRagContext(List.of(chunk1, chunk2));

        assertFalse(formatted.contains("Document 1"));
        assertFalse(formatted.contains("Document 2"));
        assertFalse(formatted.contains("Document "));
        assertFalse(formatted.contains("Document 1 says"));
        assertFalse(formatted.contains("Document 2 explains"));
        assertFalse(formatted.contains("According to Document"));
        assertTrue(formatted.contains("### Context"));
    }

    @Test
    void ragSystemPrompt_containsAnswerQualityRules() {
        String prompt = openAIService.ragSystemPrompt();

        assertTrue(prompt.contains("Quick Answer"));
        assertTrue(prompt.contains("answer the user's question immediately"));
        assertTrue(prompt.contains("clearest explanation"));
        assertTrue(prompt.contains("Never narrate sources as Document 1/2/3"));
        assertTrue(prompt.contains("FULL") && prompt.contains("PARTIAL") && prompt.contains("INSUFFICIENT"));
        assertTrue(prompt.contains("missingTopics"));

        // Phase 1.5: proportional Quick Answer (no hard sentence count)
        assertTrue(prompt.contains("concise, direct answer"));
        assertTrue(prompt.contains("length should match complexity"));
        assertTrue(prompt.contains("Do not summarize the entire procedure"));

        // Phase 1.5: detail by need + mandatory prerequisites
        assertTrue(prompt.contains("level of detail needed"));
        assertTrue(prompt.contains("prerequisite is mandatory"));
        assertTrue(prompt.contains("Do not explain optional or downstream"));

        // Phase 1.5: primary programs only
        assertTrue(prompt.contains("primary programs directly involved"));
        assertTrue(prompt.contains("avoid long supporting-program"));

        // Phase 1.5: no URLs / omit References in answer
        assertTrue(prompt.contains("Do not include ## References"));
        assertTrue(prompt.contains("Never put http:// or https://"));
        assertTrue(prompt.contains("Never emit URLs in \"answer\""));
        assertFalse(prompt.contains("required — 2–3 lines"));
        assertFalse(prompt.contains("List each reference once under ## References"));

        // Final Answer Quality Refinement: scope, layers, priority, knowledge pool
        assertTrue(prompt.contains("best matches the user's wording and the strongest supporting evidence"));
        assertTrue(prompt.contains("Never provide multiple complete procedures"));
        assertTrue(prompt.contains("shared knowledge pool, not a checklist"));
        assertTrue(prompt.contains("Do not try to cover every retrieved document"));
        assertTrue(prompt.contains("Do not feel obligated to use every chunk"));
        assertTrue(prompt.contains("then stop"));
        assertTrue(prompt.contains("exclude information that is not required"));
        assertTrue(prompt.contains("required to answer the user's question or are explicitly requested"));
        assertFalse(prompt.contains("for example \"order type\""));
        assertFalse(prompt.contains("merge all context"));
        assertFalse(prompt.contains("highest-relevance path"));
    }

    @Test
    void buildRagUserPrompt_isContextAndQuestionOnly() {
        String userPrompt = openAIService.buildRagUserPrompt("### Context\nTitle: Test\n\nBody", "How to register a customer?");

        assertTrue(userPrompt.contains("Context from M3 Documentation:"));
        assertTrue(userPrompt.contains("### Context\nTitle: Test\n\nBody"));
        assertTrue(userPrompt.contains("Question: How to register a customer?"));
        assertFalse(userPrompt.contains("missingTopics"));
        assertFalse(userPrompt.contains("FULL|PARTIAL|INSUFFICIENT"));
        assertFalse(userPrompt.contains("Respond with ONLY a JSON object"));
    }
}
