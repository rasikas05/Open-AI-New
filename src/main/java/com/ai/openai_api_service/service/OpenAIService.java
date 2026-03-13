package com.ai.openai_api_service.service;

import com.ai.openai_api_service.exception.OpenAIException;
import com.ai.openai_api_service.model.ChatRequest;
import com.ai.openai_api_service.model.ChatResponse;
import com.ai.openai_api_service.model.MessageDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class OpenAIService {

    private final RestTemplate restTemplate;

    @Value("${openai.api.key}")
    private String apiKey;

    @Value("${openai.api.model}")
    private String model;

    @Value("${openai.api.url}")
    private String openaiUrl;

    public OpenAIService() {
        this.restTemplate = new RestTemplate();
    }

    public ChatResponse chat(ChatRequest request) {
        List<Map<String, String>> messages = new ArrayList<>();

        if (request.getHistory() != null) {
            for (MessageDto message : request.getHistory()) {
                Map<String, String> map = new HashMap<>();
                map.put("role", message.getRole());
                map.put("content", message.getContent());
                messages.add(map);
            }
        }

        Map<String, String> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", request.getUserMessage());
        messages.add(userMessage);

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", messages);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        Map<String, Object> response;
        try {
            response = restTemplate.postForObject(openaiUrl, entity, Map.class);
        } catch (HttpClientErrorException e) {
            int code = e.getStatusCode().value();
            String msg = code == 401
                    ? "OpenAI API key is invalid or missing. Check openai.api.key in application.properties (no quotes)."
                    : "OpenAI API error: " + code + " " + e.getStatusText();
            throw new OpenAIException(msg, code);
        }

        if (response == null) {
            return new ChatResponse("No response from OpenAI.", true);
        }

        Object choicesObj = response.get("choices");
        if (!(choicesObj instanceof List<?> choicesList) || choicesList.isEmpty()) {
            return new ChatResponse("No choices returned from OpenAI.", true);
        }

        Object firstChoice = choicesList.get(0);
        if (!(firstChoice instanceof Map<?, ?> choiceMap)) {
            return new ChatResponse("Unexpected response format from OpenAI.", true);
        }

        Object messageObj = choiceMap.get("message");
        if (!(messageObj instanceof Map<?, ?> messageMap)) {
            return new ChatResponse("Unexpected message format from OpenAI.", true);
        }

        Object contentObj = messageMap.get("content");
        String content = contentObj != null ? contentObj.toString() : "";

        boolean truncated = false;
        Object finishReason = choiceMap.get("finish_reason");
        if (finishReason != null && "length".equals(finishReason.toString())) {
            truncated = true;
        }

        return new ChatResponse(content, truncated);
    }
}

