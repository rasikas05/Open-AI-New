package com.ai.openai_api_service.service;

import com.ai.openai_api_service.config.RestTemplateFactory;
import com.ai.openai_api_service.model.ChatRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Compare Chat Completions vs Responses API for {@link OpenAIService#chatWithoutPersistence}.
 * Skips unless OPENAI_API_KEY is set.
 *
 * Run:
 * mvnw test -Dtest=ResponsesPocComparisonTest
 *
 * Optional: -Dresponses.poc.out=path/to/responses_poc_samples.jsonl
 */
@Tag("perf")
class ResponsesPocComparisonTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Multi-turn M3 conversational script (5 turns). */
    private static final String[] TURN_QUESTIONS = {
            "What is OIS300 in Infor M3?",
            "What are its main functions?",
            "How does it relate to customer orders?",
            "What programs are commonly used with it?",
            "Summarize our discussion in two sentences."
    };

    @Test
    void compareCompletionsVsResponses_writesJsonl() throws Exception {
        String apiKey = System.getenv("OPENAI_API_KEY");
        Assumptions.assumeTrue(apiKey != null && !apiKey.isBlank(), "OPENAI_API_KEY required");

        ChatPersistenceService persistence = mock(ChatPersistenceService.class);
        AtomicReference<String> chainId = new AtomicReference<>();
        when(persistence.findLatestOpenAiResponseId(anyString(), anyString(), anyString()))
                .thenAnswer(inv -> chainId.get());

        OpenAiResponsesClient responsesClient = new OpenAiResponsesClient(120_000);
        ReflectionTestUtils.setField(responsesClient, "apiKey", apiKey);

        OpenAIService service = new OpenAIService(null, persistence, null, null, responsesClient);
        ReflectionTestUtils.setField(service, "apiKey", apiKey);
        ReflectionTestUtils.setField(service, "openaiUrl", "https://api.openai.com/v1/chat/completions");
        ReflectionTestUtils.setField(service, "restTemplate", RestTemplateFactory.create(120_000));
        ReflectionTestUtils.setField(service, "model", System.getenv().getOrDefault("OPENAI_MODEL", "gpt-5.6-terra"));
        ReflectionTestUtils.setField(responsesClient, "responsesUrl", "https://api.openai.com/v1/responses");
        ReflectionTestUtils.setField(service, "reasoningEffort", "none");
        ReflectionTestUtils.setField(service, "defaultMaxCompletionTokens", 1024);
        ReflectionTestUtils.setField(service, "systemPromptEnabled", false);
        ReflectionTestUtils.setField(service, "removeAnonymizationPlaceholders", true);
        ReflectionTestUtils.setField(service, "includeSanitizationDebug", false);
        ReflectionTestUtils.setField(service, "loadHistoryFromDb", false);
        ReflectionTestUtils.setField(service, "allowClientHistory", false);

        List<Map<String, Object>> samples = new ArrayList<>();
        samples.addAll(runSession(service, "completions", false, null));
        chainId.set(null);
        samples.addAll(runSession(service, "responses", true, chainId));

        Path out = Path.of(System.getProperty("responses.poc.out", "target/responses_poc_samples.jsonl"));
        Files.createDirectories(out.getParent());
        for (Map<String, Object> row : samples) {
            Files.writeString(
                    out,
                    MAPPER.writeValueAsString(row) + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        }
    }

    private List<Map<String, Object>> runSession(
            OpenAIService service,
            String mode,
            boolean responsesPoc,
            AtomicReference<String> chainId
    ) {
        ReflectionTestUtils.setField(service, "responsesPocEnabled", responsesPoc);

        List<Map<String, Object>> rows = new ArrayList<>();
        ChatRequest request = new ChatRequest();
        request.setTenantCode("poc-tenant");
        request.setUserId("poc-user");
        request.setSessionId("poc-session-" + mode);

        for (int turn = 0; turn < TURN_QUESTIONS.length; turn++) {
            request.setUserMessage(TURN_QUESTIONS[turn]);
            long start = System.currentTimeMillis();
            var response = service.chatWithoutPersistence(request);
            long elapsed = System.currentTimeMillis() - start;

            if (chainId != null && response.getOpenAiResponseId() != null) {
                chainId.set(response.getOpenAiResponseId());
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("mode", mode);
            row.put("turn", turn + 1);
            row.put("question", TURN_QUESTIONS[turn]);
            row.put("latencyMs", elapsed);
            row.put("responseId", response.getOpenAiResponseId());
            if (response.getOpenAiUsage() != null) {
                row.put("promptTokens", response.getOpenAiUsage().getPromptTokens());
                row.put("completionTokens", response.getOpenAiUsage().getCompletionTokens());
                row.put("totalTokens", response.getOpenAiUsage().getTotalTokens());
            }
            row.put("replyPreview", preview(response.getReply()));
            rows.add(row);
        }
        return rows;
    }

    private static String preview(String reply) {
        if (reply == null) {
            return "";
        }
        String trimmed = reply.trim();
        return trimmed.length() <= 120 ? trimmed : trimmed.substring(0, 120) + "...";
    }
}
