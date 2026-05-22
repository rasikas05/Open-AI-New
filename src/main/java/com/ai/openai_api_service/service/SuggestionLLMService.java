package com.ai.openai_api_service.service;

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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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

    public List<SuggestionItem> suggest(SuggestionContext context, int minCount, int maxCount) {
        if (!llmEnabled || context == null || apiKey == null || apiKey.isBlank()) {
            return List.of();
        }

        String prompt = buildPrompt(context, minCount, maxCount);
        if (prompt.isBlank()) {
            return List.of();
        }

        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of(
                                "role", "system",
                                "content", "You are an expert Infor M3 assistant. Generate concise, user-centric follow-up suggestions based on the user query, answer, and retrieval context."
                        ),
                        Map.of(
                                "role", "user",
                                "content", prompt
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
            return extractSuggestions(response, maxCount);
        } catch (Exception e) {
            log.warn("LLM suggestion generation failed: {}", e.getMessage());
            return List.of();
        }
    }

    private String buildPrompt(SuggestionContext context, int minCount, int maxCount) {
        String message = context.getUserMessage() == null ? "" : context.getUserMessage().trim();
        String answer = context.getAnswer() == null ? "" : context.getAnswer().trim();
        int sourceCount = context.getSources() == null ? 0 : context.getSources().size();

        StringBuilder builder = new StringBuilder();
        builder.append("You are an expert Infor M3 business process assistant. Generate practical, task-oriented follow-up suggestions.\n\n");
        builder.append("User question: ").append(message).append("\n");
        if (!answer.isBlank()) {
            builder.append("Assistant answer: ").append(answer).append("\n");
        }
        builder.append("Retrieved source count: ").append(sourceCount).append("\n\n");
        
        builder.append("CRITICAL REQUIREMENTS:\n");
        builder.append("1. Generate ONLY complete, actionable suggestions (minimum 4 words, full sentences)\n");
        builder.append("2. NEVER generate incomplete phrases ending with: in, of, for, with, and, to, by, from, is, can be, are\n");
        builder.append("3. Focus on PRACTICAL operations: how-to steps, troubleshooting, API usage, configuration, navigation\n");
        builder.append("4. AVOID generic topics like 'Customer order process', 'Purchase order information', 'Explore X'\n");
        builder.append("5. AVOID marketing or informational topics unrelated to actual tasks\n");
        builder.append("6. Ensure each suggestion helps the user continue their workflow naturally\n");
        builder.append("7. Each suggestion must be a COMPLETE thought, not a fragment\n\n");
        
        builder.append("GOOD EXAMPLES:\n");
        builder.append("- 'How to monitor purchase order status in PPS200'\n");
        builder.append("- 'Common reasons for delayed purchase orders'\n");
        builder.append("- 'How to approve customer orders in OIS100'\n");
        builder.append("- 'Which APIs are related to OIS100'\n");
        builder.append("- 'Troubleshoot delivery receipt issues'\n\n");
        
        builder.append("BAD EXAMPLES (DO NOT GENERATE):\n");
        builder.append("- 'How to track purchase order in' (incomplete)\n");
        builder.append("- 'Benefits of ad' (fragment)\n");
        builder.append("- 'Customer order process' (generic)\n");
        builder.append("- 'Explore order' (vague)\n\n");
        
        builder.append("Generate between ").append(minCount).append(" and ").append(maxCount)
                .append(" suggestions. Output ONLY a valid JSON array of objects with:\n")
                .append("- text: complete, actionable suggestion (not a fragment)\n")
                .append("- category: one of FOLLOW_UP, TROUBLESHOOTING, CONFIGURATION, API_RELATED, BEST_PRACTICE\n")
                .append("- relevance_score: number from 0.0 to 1.0\n\n")
                .append("Ensure ALL suggestions are complete sentences with proper structure. Do not include markdown or explanations.\n");
        
        return builder.toString();
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
        if (content.isBlank()) {
            return List.of();
        }

        try {
            List<Map<String, Object>> rawItems = objectMapper.readValue(
                    content,
                    new TypeReference<>() {
                    }
            );
            List<SuggestionItem> items = new ArrayList<>();
            for (Map<String, Object> raw : rawItems) {
                if (raw == null) {
                    continue;
                }
                String text = raw.getOrDefault("text", "").toString().trim();
                String categoryText = raw.getOrDefault("category", "GENERIC").toString();
                double score = parseScore(raw.get("relevance_score"));
                SuggestionCategory category = SuggestionCategory.fromString(categoryText);
                
                // Filter out incomplete or low-quality suggestions
                if (!isCompleteAndValid(text)) {
                    log.debug("Filtered incomplete LLM suggestion: {}", text);
                    continue;
                }
                
                items.add(new SuggestionItem(text, category, score, "LLM"));
                if (items.size() >= maxCount) {
                    break;
                }
            }
            return items;
        } catch (Exception parseError) {
            log.warn("LLM suggestion parsing failed, trying fallback parse as string list: {}", parseError.getMessage());
        }

        try {
            List<String> rawTexts = objectMapper.readValue(content, new TypeReference<>() {
            });
            List<SuggestionItem> items = new ArrayList<>();
            for (String rawText : rawTexts) {
                if (rawText == null || rawText.isBlank()) {
                    continue;
                }
                rawText = rawText.trim();
                if (!isCompleteAndValid(rawText)) {
                    log.debug("Filtered incomplete fallback suggestion: {}", rawText);
                    continue;
                }
                items.add(new SuggestionItem(rawText, SuggestionCategory.RELATED_TOPIC, 0.55d, "LLM"));
                if (items.size() >= maxCount) {
                    break;
                }
            }
            return items;
        } catch (Exception fallbackError) {
            log.warn("LLM fallback parsing also failed: {}", fallbackError.getMessage());
            return List.of();
        }
    }
    
    private boolean isCompleteAndValid(String text) {
        try {
            if (text == null || text.isBlank()) {
                return false;
            }
            
            String trimmed = text.trim();
            if (trimmed.isEmpty()) {
                return false;
            }
            
            // Minimum 4 words and reasonable length
            String[] words = trimmed.split("\\s+");
            int wordCount = words.length;
            if (trimmed.length() < 15 || wordCount < 4) {
                log.debug("LLM suggestion filtered (too short): chars={}, words={}, text={}", trimmed.length(), wordCount, text);
                return false;
            }
            
            // Check for incomplete phrase endings
            String lower = trimmed.toLowerCase(Locale.ROOT);
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

    private double parseScore(Object rawScore) {
        if (rawScore instanceof Number number) {
            return Math.max(0.0d, Math.min(1.0d, number.doubleValue()));
        }
        if (rawScore != null) {
            try {
                return Math.max(0.0d, Math.min(1.0d, Double.parseDouble(rawScore.toString())));
            } catch (NumberFormatException ignored) {
            }
        }
        return 0.5d;
    }
}
