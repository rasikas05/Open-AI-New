package com.ai.openai_api_service.service;

import com.ai.openai_api_service.config.RestTemplateFactory;
import com.ai.openai_api_service.exception.AiServiceErrors;
import com.ai.openai_api_service.exception.OpenAIException;
import com.ai.openai_api_service.model.OpenAIUsage;
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

import java.util.Map;

/**
 * HTTP client for OpenAI Responses API (chatWithoutPersistence POC only).
 */
@Service
public class OpenAiResponsesClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiResponsesClient.class);

    private final RestTemplate restTemplate;

    @Value("${openai.api.key}")
    private String apiKey;

    @Value("${openai.api.responses-url:https://api.openai.com/v1/responses}")
    private String responsesUrl;

    public OpenAiResponsesClient(@Value("${openai.api.timeout-ms:120000}") int openAiTimeoutMs) {
        this.restTemplate = RestTemplateFactory.create(openAiTimeoutMs);
    }

    public record ResponsesCallResult(
            String content,
            boolean truncated,
            OpenAIUsage usage,
            long elapsedMs,
            String responseId
    ) {
    }

    public ResponsesCallResult call(
            String model,
            String reasoningEffort,
            int maxOutputTokens,
            String systemContent,
            String userContent,
            String previousResponseId
    ) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new OpenAIException(
                    "OpenAI API key is missing. Set OPENAI_API_KEY env var or openai.api.key property.",
                    401
            );
        }

        Map<String, Object> body = OpenAiResponsesRequestBuilder.buildResponsesBody(
                model,
                reasoningEffort,
                maxOutputTokens,
                systemContent,
                userContent,
                previousResponseId
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        log.info(
                "Calling OpenAI Responses API. model={}, previousResponseIdPresent={}",
                model,
                previousResponseId != null && !previousResponseId.isBlank()
        );
        long start = System.currentTimeMillis();

        Map<String, Object> response;
        try {
            ResponseEntity<Map> responseEntity = restTemplate.exchange(
                    responsesUrl,
                    HttpMethod.POST,
                    entity,
                    Map.class
            );
            response = responseEntity.getBody();
        } catch (HttpClientErrorException e) {
            handleOpenAiError(e);
            throw new OpenAIException("OpenAI Responses call failed", e.getStatusCode().value());
        } catch (ResourceAccessException e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("OpenAI Responses request failed after {}ms: {}", elapsed, e.getMessage());
            throw new OpenAIException(
                    "OpenAI Responses request timed out or failed after " + elapsed + "ms: " + e.getMessage(),
                    504
            );
        }

        long elapsed = System.currentTimeMillis() - start;
        log.info("OpenAI Responses API completed in {}ms", elapsed);

        if (response == null) {
            throw new OpenAIException("No response from OpenAI Responses API.", 502);
        }

        String content = OpenAiResponsesParser.extractOutputText(response);
        boolean truncated = OpenAiResponsesParser.isTruncated(response);
        OpenAIUsage usage = OpenAiResponsesParser.extractUsage(response, model);
        String responseId = OpenAiResponsesParser.extractResponseId(response);

        return new ResponsesCallResult(content, truncated, usage, elapsed, responseId);
    }

    private void handleOpenAiError(HttpClientErrorException e) {
        int code = e.getStatusCode().value();
        String body = e.getResponseBodyAsString();
        log.warn("OpenAI Responses error status={} body={}", e.getStatusCode(), body);
        if (AiServiceErrors.isQuotaOrCreditExhaustion(body) || AiServiceErrors.isQuotaOrCreditExhaustion(e.getMessage())) {
            throw AiServiceErrors.unavailable("OpenAI Responses status=" + code + " body=" + body);
        }
        String msg = code == 401
                ? "OpenAI API key is invalid or missing."
                : "OpenAI Responses API error: " + code + " " + e.getStatusText();
        throw new OpenAIException(msg, code);
    }
}
