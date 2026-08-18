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
    void ragSystemPrompt_containsCoverageAndGroundingRules() {
        String prompt = openAIService.ragSystemPrompt();

        assertTrue(prompt.contains("FULL") && prompt.contains("PARTIAL") && prompt.contains("INSUFFICIENT"));
        assertTrue(prompt.contains("missingTopics"));
        assertTrue(prompt.contains("Do not use general M3 knowledge"));
        assertTrue(prompt.contains("Only RETRIEVED DOCUMENTATION is authoritative factual evidence"));
        assertTrue(prompt.contains("never treat previous user or assistant messages as documentation evidence"));
        assertTrue(prompt.contains("related information is INSUFFICIENT, not PARTIAL"));
        assertTrue(prompt.contains("Do not include http:// or https://"));
        assertTrue(prompt.contains("Treat the retrieved Infor M3 documentation as the source of truth"));
        assertTrue(prompt.contains("Never invent references or URLs"));

        assertFalse(prompt.contains("Presentation Guidance"));
        assertFalse(prompt.contains("shared knowledge pool, not a checklist"));
        assertFalse(prompt.contains("minimize the user's effort to understand the answer"));
    }

    @Test
    void buildRagUserPrompt_containsUserQuestionAndRetrievedDocumentation() {
        String context = "### Context\nTitle: Test\n\nBody";
        String question = "How to register a customer?";
        String userPrompt = openAIService.buildRagUserPrompt(context, question);

        assertTrue(userPrompt.startsWith("USER QUESTION:\n"));
        assertTrue(userPrompt.contains("RETRIEVED DOCUMENTATION:\n"));
        assertTrue(userPrompt.contains(question));
        assertTrue(userPrompt.contains(context));

        int questionIndex = userPrompt.indexOf("USER QUESTION:");
        int docsIndex = userPrompt.indexOf("RETRIEVED DOCUMENTATION:");
        assertTrue(questionIndex >= 0);
        assertTrue(docsIndex > questionIndex, "USER QUESTION must appear before RETRIEVED DOCUMENTATION");

        assertFalse(userPrompt.contains("Context from M3 Documentation:"));
        assertFalse(userPrompt.contains("missingTopics"));
        assertFalse(userPrompt.contains("FULL|PARTIAL|INSUFFICIENT"));
    }
}
