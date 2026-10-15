package com.ai.openai_api_service.service;

import com.ai.openai_api_service.config.RestTemplateFactory;
import com.ai.openai_api_service.exception.AiServiceErrors;
import com.ai.openai_api_service.exception.OpenAIException;
import com.ai.openai_api_service.model.OpenAIUsage;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Shared OpenAI-compatible Chat Completions client.
 * Selects OpenAI or Bedrock Runtime from {@code llm.provider}.
 * Application defaults use {@code bedrock}; Spring property fallback is {@code openai} if unset.
 * Never falls back across providers when credentials are missing.
 */
@Service
public class LlmChatCompletionsClient {

    private static final Logger log = LoggerFactory.getLogger(LlmChatCompletionsClient.class);

    public static final String PROVIDER_OPENAI = "openai";
    public static final String PROVIDER_BEDROCK = "bedrock";

    @Value("${llm.provider:openai}")
    private String provider;

    @Value("${openai.api.key:}")
    private String openaiApiKey;

    @Value("${openai.api.model:gpt-5.6-terra}")
    private String openaiModel;

    @Value("${openai.api.url:https://api.openai.com/v1/chat/completions}")
    private String openaiUrl;

    @Value("${openai.api.timeout-ms:120000}")
    private int openaiTimeoutMs;

    @Value("${openai.api.reasoning-effort:none}")
    private String openaiReasoningEffort;

    @Value("${openai.api.max-completion-tokens:4096}")
    private int openaiMaxCompletionTokens;

    @Value("${bedrock.runtime.api-key:}")
    private String bedrockApiKey;

    @Value("${bedrock.runtime.model:global.openai.gpt-5.6-terra}")
    private String bedrockModel;

    @Value("${bedrock.runtime.url:https://bedrock-runtime.eu-west-1.amazonaws.com/openai/v1/chat/completions}")
    private String bedrockUrl;

    @Value("${bedrock.runtime.timeout-ms:120000}")
    private int bedrockTimeoutMs;

    @Value("${bedrock.runtime.reasoning-effort:none}")
    private String bedrockReasoningEffort;

    @Value("${bedrock.runtime.max-completion-tokens:4096}")
    private int bedrockMaxCompletionTokens;

    private RestTemplate restTemplate;

    @PostConstruct
    void init() {
        validateProviderConfigured();
        this.restTemplate = RestTemplateFactory.create(activeTimeoutMs());
    }

    /** Package-visible for unit tests that inject a mock RestTemplate. */
    void setRestTemplate(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public void validateProviderConfigured() {
        String normalized = normalizeProvider(provider);
        if (!PROVIDER_OPENAI.equals(normalized) && !PROVIDER_BEDROCK.equals(normalized)) {
            throw new IllegalStateException(
                    "Unsupported llm.provider='" + provider + "'. Allowed values: openai, bedrock."
            );
        }
    }

    public boolean isBedrock() {
        return PROVIDER_BEDROCK.equals(normalizeProvider(provider));
    }

    public String activeProvider() {
        validateProviderConfigured();
        return isBedrock() ? PROVIDER_BEDROCK : PROVIDER_OPENAI;
    }

    public String activeModel() {
        return isBedrock() ? bedrockModel : openaiModel;
    }

    public String activeUrl() {
        return isBedrock() ? bedrockUrl : openaiUrl;
    }

    public String activeApiKey() {
        return isBedrock() ? bedrockApiKey : openaiApiKey;
    }

    public String activeReasoningEffort() {
        return isBedrock() ? bedrockReasoningEffort : openaiReasoningEffort;
    }

    public int activeDefaultMaxCompletionTokens() {
        return isBedrock() ? bedrockMaxCompletionTokens : openaiMaxCompletionTokens;
    }

    public int activeTimeoutMs() {
        return isBedrock() ? bedrockTimeoutMs : openaiTimeoutMs;
    }

    public boolean hasApiKey() {
        String key = activeApiKey();
        return key != null && !key.isBlank();
    }

    public void requireApiKey() {
        validateProviderConfigured();
        if (!hasApiKey()) {
            String hint = isBedrock()
                    ? "Set BEDROCK_API_KEY env var or bedrock.runtime.api-key property."
                    : "Set OPENAI_API_KEY env var or openai.api.key property.";
            throw new OpenAIException(
                    activeProviderLabel() + " API key is missing. " + hint,
                    401
            );
        }
    }

    /**
     * POST chat completions. When {@code maxCompletionTokens} is null, uses the active provider default.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public ChatCompletionResult complete(
            List<Map<String, String>> messages,
            Double temperature,
            Integer maxCompletionTokens,
            boolean jsonObjectResponse
    ) {
        requireApiKey();
        if (restTemplate == null) {
            this.restTemplate = RestTemplateFactory.create(activeTimeoutMs());
        }

        String model = activeModel();
        String reasoning = activeReasoningEffort();
        int defaultCap = activeDefaultMaxCompletionTokens();
        Integer tokenArg = maxCompletionTokens;

        Map<String, Object> body = OpenAiChatRequestBuilder.buildChatCompletionBody(
                model,
                reasoning,
                defaultCap,
                messages,
                temperature,
                tokenArg,
                jsonObjectResponse
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(activeApiKey());

        long start = System.currentTimeMillis();
        log.info(
                "Calling {} chat completions. model={}, reasoningEffort={}, messageCount={}, jsonObject={}",
                activeProviderLabel(),
                model,
                OpenAiChatRequestBuilder.effectiveReasoningEffort(model, reasoning),
                messages != null ? messages.size() : 0,
                jsonObjectResponse
        );

        Map<String, Object> response;
        try {
            ResponseEntity<Map> responseEntity = restTemplate.exchange(
                    activeUrl(),
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    Map.class
            );
            response = responseEntity.getBody();
        } catch (HttpClientErrorException e) {
            throw mapClientError(e);
        } catch (ResourceAccessException e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("{} request failed after {}ms: {}", activeProviderLabel(), elapsed, e.getMessage());
            throw new OpenAIException(
                    activeProviderLabel() + " request timed out or failed after " + elapsed + "ms: " + e.getMessage(),
                    504
            );
        }

        long elapsed = System.currentTimeMillis() - start;
        log.info("{} chat completions completed in {}ms", activeProviderLabel(), elapsed);

        if (response == null) {
            throw new OpenAIException("No response from " + activeProviderLabel() + ".", 502);
        }

        String content = extractContent(response);
        boolean truncated = isTruncated(response);
        OpenAIUsage usage = extractUsage(response, model);
        return new ChatCompletionResult(content, truncated, usage, elapsed, response);
    }

    public ChatCompletionResult complete(
            List<Map<String, String>> messages,
            Double temperature,
            Integer maxCompletionTokens
    ) {
        return complete(messages, temperature, maxCompletionTokens, false);
    }

    OpenAIException mapClientError(HttpClientErrorException e) {
        int code = e.getStatusCode().value();
        HttpHeaders h = e.getResponseHeaders();
        String label = activeProviderLabel();
        if (h != null) {
            log.warn("{} x-ratelimit-limit-requests={}", label, h.getFirst("x-ratelimit-limit-requests"));
            log.warn("{} x-ratelimit-remaining-requests={}", label, h.getFirst("x-ratelimit-remaining-requests"));
            log.warn("{} x-ratelimit-reset-requests={}", label, h.getFirst("x-ratelimit-reset-requests"));
            log.warn("{} x-ratelimit-limit-tokens={}", label, h.getFirst("x-ratelimit-limit-tokens"));
            log.warn("{} x-ratelimit-remaining-tokens={}", label, h.getFirst("x-ratelimit-remaining-tokens"));
            log.warn("{} x-ratelimit-reset-tokens={}", label, h.getFirst("x-ratelimit-reset-tokens"));
        }
        String body = e.getResponseBodyAsString();
        log.warn("{} error status={} body={}", label, e.getStatusCode(), body);
        if (AiServiceErrors.isQuotaOrCreditExhaustion(body) || AiServiceErrors.isQuotaOrCreditExhaustion(e.getMessage())) {
            throw AiServiceErrors.unavailable(label + " status=" + code + " body=" + body);
        }
        String msg = code == 401
                ? label + " API key is invalid or missing. Check provider credentials (no quotes)."
                : label + " API error: " + code + " " + e.getStatusText();
        return new OpenAIException(msg, code);
    }

    String extractContent(Map<String, Object> response) {
        Object choicesObj = response.get("choices");
        if (!(choicesObj instanceof List<?> choicesList) || choicesList.isEmpty()) {
            throw new OpenAIException("No choices returned from " + activeProviderLabel() + ".", 502);
        }
        Object firstChoice = choicesList.get(0);
        if (!(firstChoice instanceof Map<?, ?> choiceMap)) {
            throw new OpenAIException("Unexpected response format from " + activeProviderLabel() + ".", 502);
        }
        Object messageObj = choiceMap.get("message");
        if (!(messageObj instanceof Map<?, ?> messageMap)) {
            throw new OpenAIException("Unexpected message format from " + activeProviderLabel() + ".", 502);
        }
        Object contentObj = messageMap.get("content");
        return contentObj != null ? contentObj.toString() : "";
    }

    boolean isTruncated(Map<String, Object> response) {
        Object choicesObj = response.get("choices");
        if (!(choicesObj instanceof List<?> choicesList) || choicesList.isEmpty()) {
            return false;
        }
        Object firstChoice = choicesList.get(0);
        if (!(firstChoice instanceof Map<?, ?> choiceMap)) {
            return false;
        }
        Object finishReason = choiceMap.get("finish_reason");
        return finishReason != null && "length".equalsIgnoreCase(finishReason.toString());
    }

    OpenAIUsage extractUsage(Map<String, Object> response, String modelUsed) {
        OpenAIUsage usage = new OpenAIUsage();
        usage.setModel(modelUsed);
        Object usageObj = response.get("usage");
        if (!(usageObj instanceof Map<?, ?> usageMap)) {
            usage.setPromptTokens(0);
            usage.setCompletionTokens(0);
            usage.setTotalTokens(0);
            return usage;
        }
        usage.setPromptTokens(toInt(usageMap.get("prompt_tokens")));
        usage.setCompletionTokens(toInt(usageMap.get("completion_tokens")));
        usage.setTotalTokens(toInt(usageMap.get("total_tokens")));
        return usage;
    }

    private Integer toInt(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        return 0;
    }

    public String activeProviderLabel() {
        return isBedrock() ? "Bedrock" : "OpenAI";
    }

    static String normalizeProvider(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public record ChatCompletionResult(
            String content,
            boolean truncated,
            OpenAIUsage usage,
            long elapsedMs,
            Map<String, Object> rawResponse
    ) {
    }
}
