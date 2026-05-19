package com.ai.openai_api_service.service;

import com.ai.openai_api_service.model.ChatRequest;
import com.ai.openai_api_service.model.MessageDto;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SuggestionLLMService {

    private static final Logger log = LoggerFactory.getLogger(SuggestionLLMService.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${openai.api.key}")
    private String apiKey;

    @Value("${openai.api.model}")
    private String model;

    @Value("${openai.api.url}")
    private String openaiUrl;

    @Value("${suggestion.llm.enabled:true}")
    private boolean llmEnabled;

    public List<String> suggest(ChatRequest request, int minCount, int maxCount) {
        if (!llmEnabled || request == null || apiKey == null || apiKey.isBlank()) {
            return List.of();
        }

        String context = buildContext(request);
        if (context.isBlank()) {
            return List.of();
        }

        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of(
                                "role", "system",
                                "content", "You are an Infor M3 assistant. Generate short user-centric suggestion chips."
                        ),
                        Map.of(
                                "role", "user",
                                "content", "Based on this context, generate " + minCount + " to " + maxCount +
                                        " short, topic-based related search phrases or help topics. Each suggestion must be a concise, end-user learning topic that is AI-ready and domain-relevant. Avoid UI action or navigation tasks, including starting with 'View', 'Check', 'Track', 'See', 'Open', or 'Go to'. Avoid long conversational questions and assistant-style phrasing such as 'Do you want', 'Should I', or 'Would you like'. Do NOT use the words inquiry, processing, or management. Do NOT generate suggestions that assume system execution capabilities or imply exporting files, fetching live ERP data, summarizing database values, retrieving customer records, sending emails, or generating reports. Suggestions should feel like related informational topics users may want to explore next. Output only a JSON array of strings.\n\n" + context
                        )
                )
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        try {
            ResponseEntity<Map> responseEntity = restTemplate.exchange(
                    openaiUrl,
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    Map.class
            );
            Map<String, Object> response = responseEntity.getBody();
            List<String> suggestions = extractContent(response);
            return suggestions.stream().limit(maxCount).collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("LLM suggestion generation failed: {}", e.getMessage());
            return List.of();
        }
    }

    private String buildContext(ChatRequest request) {
        String latest = request.getUserMessage() == null ? "" : request.getUserMessage().trim();
        List<MessageDto> history = request.getHistory() == null ? List.of() : request.getHistory();

        List<String> contextLines = new ArrayList<>();
        int from = Math.max(0, history.size() - 2);
        for (int i = from; i < history.size(); i++) {
            MessageDto m = history.get(i);
            if (m.getRole() != null && m.getContent() != null && !m.getContent().isBlank()) {
                contextLines.add(m.getRole().toLowerCase(Locale.ROOT) + ": " + m.getContent().trim());
            }
        }
        if (!latest.isBlank()) {
            contextLines.add("user: " + latest);
        }
        return String.join("\n", contextLines);
    }

    private List<String> extractContent(Map<String, Object> response) {
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
        if (content.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(content, objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception e) {
            log.warn("Failed to parse LLM response as JSON array: {}", e.getMessage());
            return List.of();
        }
    }
}
