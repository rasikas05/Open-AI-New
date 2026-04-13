package com.ai.openai_api_service.service;

import com.ai.openai_api_service.model.ChatRequest;
import com.ai.openai_api_service.model.MessageDto;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class SuggestionLLMService {

    private static final Logger log = LoggerFactory.getLogger(SuggestionLLMService.class);

    private final RestTemplate restTemplate = new RestTemplate();

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
                                "content", "You are an Infor M3 assistant. Generate only short follow-up questions."
                        ),
                        Map.of(
                                "role", "user",
                                "content", "Based on this context, generate " + minCount + " to " + maxCount +
                                        " short, relevant follow-up questions. Return only plain lines, no numbering.\n\n" + context
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
            String content = extractContent(response);
            return normalize(content, maxCount);
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

    private String extractContent(Map<String, Object> response) {
        if (response == null) {
            return "";
        }
        Object choicesObj = response.get("choices");
        if (!(choicesObj instanceof List<?> choices) || choices.isEmpty()) {
            return "";
        }
        Object first = choices.get(0);
        if (!(first instanceof Map<?, ?> choiceMap)) {
            return "";
        }
        Object messageObj = choiceMap.get("message");
        if (!(messageObj instanceof Map<?, ?> messageMap)) {
            return "";
        }
        Object contentObj = messageMap.get("content");
        return contentObj == null ? "" : contentObj.toString();
    }

    private List<String> normalize(String content, int maxCount) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        String[] lines = content.split("\\r?\\n");
        Set<String> result = new LinkedHashSet<>();
        for (String line : lines) {
            String clean = line.replaceFirst("^[-*\\d.\\s]+", "").trim();
            if (clean.isBlank()) {
                continue;
            }
            if (!clean.endsWith("?")) {
                clean = clean + "?";
            }
            if (clean.length() > 140) {
                clean = clean.substring(0, 140).trim();
                if (!clean.endsWith("?")) {
                    clean = clean + "?";
                }
            }
            result.add(clean);
            if (result.size() >= maxCount) {
                break;
            }
        }
        return new ArrayList<>(result);
    }
}
