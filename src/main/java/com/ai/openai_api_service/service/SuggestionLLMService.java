package com.ai.openai_api_service.service;

import com.ai.openai_api_service.config.RestTemplateFactory;
import com.ai.openai_api_service.model.SuggestionCategory;
import com.ai.openai_api_service.model.SuggestionContext;
import com.ai.openai_api_service.model.SuggestionItem;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class SuggestionLLMService {

    private static final Logger log = LoggerFactory.getLogger(SuggestionLLMService.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${openai.api.key}")
    private String apiKey;

    @Value("${openai.api.model}")
    private String model;

    @Value("${openai.api.url}")
    private String openaiUrl;

    @Value("${suggestion.llm.enabled:true}")
    private boolean llmEnabled;

    @Value("${openai.api.reasoning-effort:none}")
    private String reasoningEffort;

    @Value("${suggestion.llm.max-completion-tokens:80}")
    private int suggestionMaxCompletionTokens;

    static final int SUGGESTION_COMPLETION_RETRY_TOKENS = 128;
    static final int ANSWER_PROMPT_MAX_CHARS = 400;
    static final int SUGGESTION_MAX_WORDS = 10;
    static final int SUGGESTION_MAX_CHARS = 60;

    public SuggestionLLMService(
            @Value("${openai.api.timeout-ms:120000}") int openAiTimeoutMs
    ) {
        this.restTemplate = RestTemplateFactory.create(openAiTimeoutMs);
    }

    public record SuggestionLlmOutcome(List<SuggestionItem> items, int promptTokens, int completionTokens) {
        public static SuggestionLlmOutcome empty() {
            return new SuggestionLlmOutcome(List.of(), 0, 0);
        }

        public int totalTokens() {
            return Math.max(0, promptTokens) + Math.max(0, completionTokens);
        }
    }

    public List<SuggestionItem> suggest(SuggestionContext context, int minCount, int maxCount) {
        return suggestWithUsage(context, minCount, maxCount).items();
    }

    public SuggestionLlmOutcome suggestWithUsage(SuggestionContext context, int minCount, int maxCount) {
        if (!llmEnabled || context == null || apiKey == null || apiKey.isBlank()) {
            return SuggestionLlmOutcome.empty();
        }

        Instant promptStart = Instant.now();
        String prompt = buildPrompt(context, minCount, maxCount);
        long promptBuildMs = Duration.between(promptStart, Instant.now()).toMillis();
        if (prompt.isBlank()) {
            return SuggestionLlmOutcome.empty();
        }

        List<Map<String, String>> messages = List.of(
                Map.of("role", "system", "content", suggestionSystemPrompt()),
                Map.of("role", "user", "content", prompt)
        );
        int tokenCap = suggestionCompletionTokenCap();
        Map<String, Object> body = OpenAiChatRequestBuilder.buildChatCompletionBody(
                model,
                reasoningEffort,
                tokenCap,
                messages,
                0.3,
                tokenCap
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        try {
            Instant openAiStart = Instant.now();
            log.debug(
                    "Calling OpenAI chat completions for suggestions. model={}, reasoningEffort={}",
                    model,
                    OpenAiChatRequestBuilder.effectiveReasoningEffort(model, reasoningEffort)
            );
            ResponseEntity<Map> responseEntity = restTemplate.exchange(
                    openaiUrl,
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    Map.class
            );
            long openAiWaitMs = Duration.between(openAiStart, Instant.now()).toMillis();
            Map<String, Object> response = responseEntity.getBody();
            Instant parseStart = Instant.now();
            List<SuggestionItem> items = extractSuggestions(response, maxCount);
            if (items.isEmpty() && isLengthTruncated(response) && tokenCap < SUGGESTION_COMPLETION_RETRY_TOKENS) {
                log.debug("Suggestion JSON truncated; retrying with max_completion_tokens={}", SUGGESTION_COMPLETION_RETRY_TOKENS);
                body = OpenAiChatRequestBuilder.buildChatCompletionBody(
                        model,
                        reasoningEffort,
                        SUGGESTION_COMPLETION_RETRY_TOKENS,
                        messages,
                        0.3,
                        SUGGESTION_COMPLETION_RETRY_TOKENS
                );
                responseEntity = restTemplate.exchange(
                        openaiUrl,
                        HttpMethod.POST,
                        new HttpEntity<>(body, headers),
                        Map.class
                );
                response = responseEntity.getBody();
                items = extractSuggestions(response, maxCount);
            }
            long parseMs = Duration.between(parseStart, Instant.now()).toMillis();
            int promptTokens = extractUsageField(response, "prompt_tokens");
            int completionTokens = extractUsageField(response, "completion_tokens");
            log.debug(
                    "Suggestion LLM Timing | model={} | promptBuildMs={} | openAiWaitMs={} | parseMs={} | llmCount={} | promptTokens={} | completionTokens={}",
                    model,
                    promptBuildMs,
                    openAiWaitMs,
                    parseMs,
                    items.size(),
                    promptTokens,
                    completionTokens
            );
            return new SuggestionLlmOutcome(items, promptTokens, completionTokens);
        } catch (Exception e) {
            log.warn("LLM suggestion generation failed: {}", e.getMessage());
            return SuggestionLlmOutcome.empty();
        }
    }

    private int extractUsageField(Map<String, Object> response, String field) {
        if (response == null) {
            return 0;
        }
        Object usageObj = response.get("usage");
        if (!(usageObj instanceof Map<?, ?> usage)) {
            return 0;
        }
        Object value = usage.get(field);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }

    private String buildPrompt(SuggestionContext context, int minCount, int maxCount) {
        return buildUserPrompt(context);
    }

    String suggestionSystemPrompt() {
        return "You write the user's next Infor M3 chat requests. "
                + "Each string must be sendable as the next user message. "
                + "Output ONLY a JSON array of exactly 2 strings.";
    }

    int suggestionCompletionTokenCap() {
        int cap = suggestionMaxCompletionTokens > 0 ? suggestionMaxCompletionTokens : 80;
        return Math.min(Math.max(cap, 48), SUGGESTION_COMPLETION_RETRY_TOKENS);
    }

    String buildUserPrompt(SuggestionContext context) {
        String message = context.getUserMessage() == null ? "" : context.getUserMessage().trim();
        String answer = truncateAnswer(context.getAnswer() == null ? "" : context.getAnswer().trim());
        StringBuilder builder = new StringBuilder();
        builder.append("User request: ").append(message).append('\n');
        if (!answer.isBlank()) {
            builder.append("Assistant answer: ").append(answer).append('\n');
        }
        builder.append("Return exactly 2 next user requests they can tap and send.\n");
        builder.append("5-8 words each, hard max 10. Prefer a concrete M3 program, field, or step from this answer.\n");
        builder.append("Two different requests. First-person question or short command.\n");
        builder.append("Never assistant voice (do you need help, I can, would you like).\n");
        builder.append("Never copy the assistant sentence. Never generic How do I create/run a report.\n");
        builder.append("JSON array of 2 strings. No objects, markdown, or extra text.");
        return builder.toString();
    }

    String truncateAnswer(String answer) {
        if (answer == null || answer.length() <= ANSWER_PROMPT_MAX_CHARS) {
            return answer == null ? "" : answer;
        }
        return answer.substring(0, ANSWER_PROMPT_MAX_CHARS).trim();
    }

    private boolean isLengthTruncated(Map<String, Object> response) {
        if (response == null) {
            return false;
        }
        Object choicesObj = response.get("choices");
        if (!(choicesObj instanceof List<?> choices) || choices.isEmpty()) {
            return false;
        }
        Object first = choices.get(0);
        if (!(first instanceof Map<?, ?> choiceMap)) {
            return false;
        }
        Object finish = choiceMap.get("finish_reason");
        return finish != null && "length".equalsIgnoreCase(finish.toString());
    }

    private List<SuggestionItem> extractSuggestions(Map<String, Object> response, int maxCount) {
        if (response == null) {
            return List.of();
        }
        Object choicesObj = response.get("choices");
        if (!(choicesObj instanceof List<?> choices) || choices.isEmpty()) {
            return List.of();
        }
        Object first = choices.get(0);
        if (!(first instanceof Map<?, ?> choiceMap)) {
            return List.of();
        }
        Object messageObj = choiceMap.get("message");
        if (!(messageObj instanceof Map<?, ?> messageMap)) {
            return List.of();
        }
        Object contentObj = messageMap.get("content");
        String content = contentObj == null ? "" : contentObj.toString();
        return parseSuggestionContent(content, maxCount);
    }

    List<SuggestionItem> parseSuggestionContent(String content, int maxCount) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        String cleaned = content.strip();
        if (cleaned.startsWith("```")) {
            int start = cleaned.indexOf('\n');
            int end = cleaned.lastIndexOf("```");
            if (start > 0 && end > start) {
                cleaned = cleaned.substring(start + 1, end).strip();
            }
        }
        int cap = Math.max(1, maxCount);
        try {
            List<String> rawTexts = objectMapper.readValue(cleaned, new TypeReference<>() {
            });
            return toItems(rawTexts, cap);
        } catch (Exception ignored) {
            // object-array fallback
        }
        try {
            List<Map<String, Object>> rawItems = objectMapper.readValue(cleaned, new TypeReference<>() {
            });
            List<String> texts = new ArrayList<>();
            for (Map<String, Object> raw : rawItems) {
                if (raw == null) {
                    continue;
                }
                texts.add(raw.getOrDefault("text", "").toString());
            }
            return toItems(texts, cap);
        } catch (Exception parseError) {
            log.warn("LLM suggestion parsing failed: {}", parseError.getMessage());
            return List.of();
        }
    }

    private List<SuggestionItem> toItems(List<String> rawTexts, int cap) {
        List<SuggestionItem> items = new ArrayList<>();
        if (rawTexts == null) {
            return items;
        }
        for (String rawText : rawTexts) {
            if (rawText == null || rawText.isBlank()) {
                continue;
            }
            String text = rawText.trim();
            if (!isCompleteAndValid(text)) {
                continue;
            }
            items.add(new SuggestionItem(text, SuggestionCategory.FOLLOW_UP, 0.8d, "LLM"));
            if (items.size() >= cap) {
                break;
            }
        }
        return items;
    }
    
    boolean isCompleteAndValid(String text) {
        try {
            if (text == null || text.isBlank()) {
                return false;
            }
            String trimmed = text.trim();
            String[] words = trimmed.split("\\s+");
            int wordCount = words.length;
            if (wordCount > SUGGESTION_MAX_WORDS || trimmed.length() > SUGGESTION_MAX_CHARS) {
                log.debug("LLM suggestion filtered (too long): chars={}, words={}, text={}", trimmed.length(), wordCount, text);
                return false;
            }
            String lower = trimmed.toLowerCase(Locale.ROOT);
            if (lower.matches("^how can i\\s+(which|what|do|how)\\b.*")) {
                log.debug("LLM suggestion filtered (glued prefix): {}", text);
                return false;
            }
            if (lower.contains("do you need help")
                    || lower.contains("would you like")
                    || lower.contains("i can help")
                    || lower.startsWith("i can ")) {
                log.debug("LLM suggestion filtered (assistant voice): {}", text);
                return false;
            }
            if (lower.matches("^how do i (create|run) (an? )?(ad hoc )?report\\??$")) {
                log.debug("LLM suggestion filtered (generic template): {}", text);
                return false;
            }
            if (lower.matches(".*\\b(in|of|for|with|and|to|by|from|is|can be|are)\\s*$")) {
                log.debug("LLM suggestion filtered (incomplete ending): {}", text);
                return false;
            }
            return true;
        } catch (Exception e) {
            log.error("Error validating LLM suggestion: {}", text, e);
            return false;
        }
    }
}
