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
        assertTrue(prompt.contains("usable how-to, procedure, MI transaction, API, Custom List"));
        assertTrue(prompt.contains("Do not replace documented steps or MI/API reference material"));
        assertTrue(prompt.contains("Do not include http:// or https://"));
        assertTrue(prompt.contains("Treat the retrieved Infor M3 documentation as the source of truth"));
        assertTrue(prompt.contains("Never invent references or URLs"));
        assertTrue(prompt.contains("Additional grounded coverage rules"));
        assertTrue(prompt.contains("Return status PARTIAL only when the user requested multiple distinct tasks"));
        assertTrue(prompt.contains("For a single-task question where the conflict prevents a reliable answer, return INSUFFICIENT"));
        assertFalse(prompt.contains("Do not apologize, refuse for reasons unrelated to documentation coverage"));
        assertFalse(prompt.contains("Write the JSON \"answer\" field in"));
        assertFalse(prompt.contains("Presentation Guidance"));
        assertFalse(prompt.contains("shared knowledge pool, not a checklist"));
        assertFalse(prompt.contains("minimize the user's effort to understand the answer"));
    }

    @Test
    void groundedSystemPrompt_namesTheResolvedDisplayLanguage() {
        String german = openAIService.groundedSystemPrompt(ChatPersistenceService.LOCALE_DE_DE);
        String english = openAIService.groundedSystemPrompt(ChatPersistenceService.LOCALE_EN_US);
        String french = openAIService.groundedSystemPrompt("fr_FR");
        String hindi = openAIService.groundedSystemPrompt("hi_IN");
        String spanish = openAIService.groundedSystemPrompt("es_ES");
        String unchanged = openAIService.groundedSystemPrompt(null);

        assertTrue(german.contains("Use the English question and the retrieved documentation as the grounding source."));
        assertTrue(german.contains("Use the original user question for intent and nuance."));
        assertTrue(german.contains("Do not invent M3 facts from the original question alone."));
        assertTrue(german.contains("Write the JSON \"answer\" field in German"));
        assertTrue(german.contains("Copy program IDs, record IDs, field names, and MI names exactly."));
        assertTrue(german.startsWith(openAIService.ragSystemPrompt()));
        assertTrue(english.contains("Write the JSON \"answer\" field in English"));
        assertTrue(french.contains("Write the JSON \"answer\" field in French"));
        assertTrue(hindi.contains("Write the JSON \"answer\" field in Hindi"));
        assertTrue(spanish.contains("Write the JSON \"answer\" field in Spanish"));
        assertFalse(unchanged.contains("Write the JSON \"answer\" field in"));
    }

    @Test
    void fallbackAndConversationalPromptsNameTheResolvedDisplayLanguage() {
        String fallback = openAIService.fallbackPrompt("de_DE");
        String conversational = openAIService.conversationalLanguageInstruction("fr_FR");
        String absent = openAIService.fallbackPrompt(null);

        assertTrue(fallback.contains("Write the reply in German"));
        assertTrue(fallback.contains("Copy program IDs, record IDs, field names, and MI names exactly."));
        assertTrue(conversational.contains("write the JSON \"response\" field in French"));
        assertTrue(openAIService.conversationalLanguageInstruction("de_DE").contains("write the JSON \"response\" field in German"));
        assertFalse(absent.contains("Write the reply in"));
        assertFalse(OpenAIService.userMessageLanguageInstruction().contains("language and script of the user message"));
        assertTrue(OpenAIService.userMessageLanguageInstruction().contains("Do not replace an ordinary question with a greeting."));
    }

    @Test
    void buildRagUserPrompt_includesProtectedOriginalAndDetectedLanguage() {
        String prompt = openAIService.buildRagUserPrompt(
                "chunk",
                "Show customer Y00111",
                "Zeige den Kunden Y00111 und [MASK]",
                "de"
        );

        assertTrue(prompt.contains("Original user question:\nZeige den Kunden Y00111 und [MASK]"));
        assertTrue(prompt.contains("Detected language: de"));
        assertTrue(prompt.contains("English question:\nShow customer Y00111"));
        assertTrue(prompt.contains("RETRIEVED DOCUMENTATION:\nchunk"));
        assertFalse(prompt.contains("Geheimnis"));
    }

    @Test
    void clearPromptAddsOnlyTheSupportedLanguageSentence() throws Exception {
        String properties;
        try (var input = getClass().getClassLoader().getResourceAsStream("application.properties")) {
            properties = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        int key = properties.indexOf("openai.assistant.system-prompt=");
        int enabled = properties.indexOf("openai.assistant.system-prompt.enabled=");
        assertTrue(key > enabled);
        String prompt = properties.substring(key, properties.indexOf('\n', key)).trim();
        assertTrue(prompt.contains("Apply CLEAR strictly."));
        assertTrue(prompt.contains("Do not expose anonymization placeholders."));
        assertTrue(prompt.endsWith(
                "This bot supports English, Hindi, Marathi, German, Spanish, and French, and answers only in those languages."
        ));
        assertFalse(prompt.contains("Reply in the language and script"));
        try (var prod = getClass().getClassLoader().getResourceAsStream("application-prod.properties")) {
            String production = new String(prod.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertFalse(production.contains("openai.assistant.system-prompt="));
        }
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
