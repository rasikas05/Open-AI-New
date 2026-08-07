package com.ai.openai_api_service.service;

import com.ai.openai_api_service.exception.OpenAIException;
import com.ai.openai_api_service.model.ChatRequest;
import com.ai.openai_api_service.model.python_rag.ChunkItem;
import com.ai.openai_api_service.model.rag.GroundedRagCallResult;
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

/**
 * Phase 3: collect ≥20 grounded stage samples (promptBuild / openAiWait / responseParse)
 * with p50/p95/max-ready JSONL. Skips unless OPENAI_API_KEY is set.
 *
 * Run: mvn -pl . -Dtest=GroundedStageTimingProfileTest#profileGroundedStages_writesJsonl test
 * Optional: -Dgrounded.perf.out=path/to/grounded_stage_samples.jsonl
 */
@Tag("perf")
class GroundedStageTimingProfileTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String[] QUESTIONS = {
            "How do I configure dispatch policy in OIS101?",
            "What is the difference between customer order and purchase order?",
            "How does warehouse location structure work?",
            "Explain purchase order type configuration in PPS095",
            "How does customer order flow work end to end?",
            "Where is pricing configured in M3?"
    };

    @Test
    void profileGroundedStages_writesJsonl() throws Exception {
        String apiKey = System.getenv("OPENAI_API_KEY");
        Assumptions.assumeTrue(apiKey != null && !apiKey.isBlank(), "OPENAI_API_KEY required");

        OpenAIService service = new OpenAIService(null, null, null);
        ReflectionTestUtils.setField(service, "apiKey", apiKey);
        ReflectionTestUtils.setField(service, "model", System.getenv().getOrDefault("OPENAI_MODEL", "gpt-4.1"));
        ReflectionTestUtils.setField(
                service,
                "openaiUrl",
                System.getenv().getOrDefault("OPENAI_API_URL", "https://api.openai.com/v1/chat/completions")
        );
        ReflectionTestUtils.setField(service, "loadHistoryFromDb", false);
        ReflectionTestUtils.setField(service, "allowClientHistory", false);
        ReflectionTestUtils.setField(service, "removeAnonymizationPlaceholders", true);
        ReflectionTestUtils.setField(service, "openAiTimeoutMs", 120_000);
        service.initRestTemplate();

        Path out = resolveOutPath();
        Files.createDirectories(out.getParent());
        List<String> existing = Files.exists(out)
                ? Files.readAllLines(out, StandardCharsets.UTF_8).stream().filter(l -> !l.isBlank()).toList()
                : List.of();
        int sampleIndex = existing.size();
        if (sampleIndex == 0) {
            Files.writeString(out, "", StandardCharsets.UTF_8);
        }

        // 6 questions × 4 chunk-count buckets = 24 samples (body sizes kept moderate for TPM limits)
        int[] chunkCounts = {2, 4, 6, 8};
        int[] bodyChars = {250, 450, 700, 1000};
        final int targetSamples = QUESTIONS.length * chunkCounts.length;

        for (int flat = sampleIndex; flat < targetSamples; flat++) {
            int q = flat / chunkCounts.length;
            int b = flat % chunkCounts.length;
            List<ChunkItem> chunks = buildChunks(chunkCounts[b], bodyChars[b], q, b);
            ChatRequest request = new ChatRequest();
            request.setUserMessage(QUESTIONS[q]);
            request.setTenantCode("perf");
            request.setUserId("perf-user");
            request.setSessionId("perf-session-" + flat);

            GroundedRagCallResult result = null;
            long wallMs = 0L;
            OpenAIException last = null;
            for (int attempt = 1; attempt <= 8; attempt++) {
                try {
                    long wallStart = System.currentTimeMillis();
                    result = service.chatWithRagContext(request, chunks);
                    wallMs = System.currentTimeMillis() - wallStart;
                    last = null;
                    break;
                } catch (OpenAIException ex) {
                    last = ex;
                    String msg = ex.getMessage() != null ? ex.getMessage() : "";
                    if (!msg.contains("429") && !msg.toLowerCase().contains("rate")) {
                        throw ex;
                    }
                    long sleepMs = Math.min(60_000L, 6_000L * attempt);
                    System.out.println("Rate limited on sample " + flat
                            + " attempt " + attempt + "; sleeping " + sleepMs + "ms");
                    Thread.sleep(sleepMs);
                }
            }
            if (result == null) {
                throw last != null ? last : new IllegalStateException("No grounded result");
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("sample", flat);
            row.put("question", QUESTIONS[q]);
            row.put("chunkCount", chunks.size());
            row.put("targetBodyChars", bodyChars[b]);
            row.put("promptBuildMs", result.promptBuildMs());
            row.put("openAiWaitMs", result.openAiWaitMs());
            row.put("responseParseMs", result.responseParseMs());
            row.put("wallMs", wallMs);
            row.put("promptContextChars", result.promptContextChars());
            row.put("promptTokens", result.usage() != null ? result.usage().getPromptTokens() : null);
            row.put("completionTokens", result.usage() != null ? result.usage().getCompletionTokens() : null);
            row.put("totalTokens", result.usage() != null ? result.usage().getTotalTokens() : null);
            row.put("status", result.grounded() != null ? String.valueOf(result.grounded().getStatus()) : null);

            Files.writeString(
                    out,
                    MAPPER.writeValueAsString(row) + "\n",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.APPEND
            );
            sampleIndex = flat + 1;
            // Pace requests under 30k TPM (gpt-4.1 tier)
            Thread.sleep(8_000L);
        }

        Assumptions.assumeTrue(sampleIndex >= 20, "Expected ≥20 samples, got " + sampleIndex);
        System.out.println("Wrote " + sampleIndex + " grounded stage samples to " + out.toAbsolutePath());
    }

    private static Path resolveOutPath() {
        String override = System.getProperty("grounded.perf.out");
        if (override != null && !override.isBlank()) {
            return Path.of(override);
        }
        Path repoRelative = Path.of("..", "m3-rag-system-py", "data", "perf", "grounded_stage_samples.jsonl");
        if (Files.isDirectory(repoRelative.getParent())) {
            return repoRelative.normalize();
        }
        return Path.of("target", "grounded_stage_samples.jsonl");
    }

    private static List<ChunkItem> buildChunks(int count, int bodyChars, int qIdx, int bIdx) {
        List<ChunkItem> chunks = new ArrayList<>(count);
        String bodyPad = "M3 documentation paragraph. ".repeat(Math.max(1, bodyChars / 28));
        if (bodyPad.length() > bodyChars) {
            bodyPad = bodyPad.substring(0, bodyChars);
        }
        for (int i = 0; i < count; i++) {
            ChunkItem c = new ChunkItem();
            c.setTitle("Doc Title Q" + qIdx + " C" + i);
            c.setSectionPath(List.of("Orders", "Setup", "Panel " + i));
            c.setProgramIds(List.of(i % 2 == 0 ? "OIS101" : "PPS095"));
            c.setSource("https://docs.example.com/m3/long/path/to/article/" + qIdx + "/" + i + "?v=" + bIdx);
            c.setScore(0.85f - (i * 0.05f));
            c.setChunk(bodyPad + " Program detail " + i + ".");
            chunks.add(c);
        }
        return chunks;
    }
}
