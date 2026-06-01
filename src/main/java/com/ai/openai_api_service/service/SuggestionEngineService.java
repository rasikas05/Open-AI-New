package com.ai.openai_api_service.service;

import com.ai.openai_api_service.model.SuggestionCategory;
import com.ai.openai_api_service.model.SuggestionContext;
import com.ai.openai_api_service.model.SuggestionItem;
import com.ai.openai_api_service.model.SuggestionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class SuggestionEngineService {
    private static final Pattern PROGRAM_PATTERN = Pattern.compile("\\b(?:AHS|CMS|OIS|MMS|CRS)\\d{3}\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern VIRTUAL_FIELDS_PATTERN = Pattern.compile("\\bvirtual fields?\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern AD_HOC_REPORT_PATTERN = Pattern.compile("\\bad hoc report(?: designer)?\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern INFORMATION_BROWSER_PATTERN = Pattern.compile("\\binformation browser\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern USERS_ROLES_PATTERN = Pattern.compile("\\busers?\\s*(?:and|&)\\s*roles?\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern METADATA_PATTERN = Pattern.compile("\\bmetadata\\b", Pattern.CASE_INSENSITIVE);

    private static final Logger log = LoggerFactory.getLogger(SuggestionEngineService.class);

    private final SuggestionRuleService suggestionRuleService;
    private final SuggestionLLMService suggestionLLMService;
    private final SuggestionCacheService suggestionCacheService;

    @Value("${suggestion.min-count:3}")
    private int minSuggestionCount;

    @Value("${suggestion.max-count:5}")
    private int maxSuggestionCount;

    @Value("${suggestion.rule.enabled:true}")
    private boolean ruleEnabled;

    @Value("${suggestion.llm.enabled:true}")
    private boolean llmEnabled;

    public SuggestionEngineService(
            SuggestionRuleService suggestionRuleService,
            SuggestionLLMService suggestionLLMService,
            SuggestionCacheService suggestionCacheService
    ) {
        this.suggestionRuleService = suggestionRuleService;
        this.suggestionLLMService = suggestionLLMService;
        this.suggestionCacheService = suggestionCacheService;
    }

    public SuggestionResult generateSuggestions(SuggestionContext context) {
        return generateSuggestions(context, minSuggestionCount, maxSuggestionCount);
    }

    public SuggestionResult generateSuggestions(SuggestionContext context, int minCount, int maxCount) {
        if (context == null || context.getUserMessage() == null || context.getUserMessage().isBlank()) {
            return new SuggestionResult(List.of(), List.of());
        }

        String userMessage = context.getUserMessage().trim();
        List<String> extractedTopics = extractTopics(context);
        boolean supportedTopic = ruleEnabled && (suggestionRuleService.isSupportedM3Topic(userMessage) || !extractedTopics.isEmpty());
        if (!supportedTopic) {
            return new SuggestionResult(List.of(), List.of());
        }

        int targetCount = Math.max(1, Math.min(maxCount, Math.max(minCount, maxCount)));
        List<SuggestionItem> topicItems = buildTopicBasedItems(context, targetCount);
        List<SuggestionItem> ruleItems = buildRuleItems(userMessage, targetCount);
        List<SuggestionItem> llmItems = llmEnabled ? getLlmSuggestions(context, targetCount) : List.of();

        List<SuggestionItem> merged = new ArrayList<>();
        if (!topicItems.isEmpty()) {
            merged.addAll(topicItems);
            merged.addAll(llmItems);
        } else if (!ruleItems.isEmpty()) {
            merged.addAll(ruleItems);
            merged.addAll(llmItems);
        }

        if (merged.isEmpty()) {
            merged.addAll(buildGenericItems(targetCount));
        }

        List<SuggestionItem> ranked = normalizeAndRankSuggestions(merged, targetCount);
        return mapToResult(ranked);
    }

    private List<SuggestionItem> buildTopicBasedItems(SuggestionContext context, int maxCount) {
        List<String> topics = extractTopics(context);
        if (topics.isEmpty()) {
            return List.of();
        }

        List<SuggestionItem> topicItems = new ArrayList<>();
        for (String topic : topics) {
            for (String suggestion : buildSuggestionsForTopic(topic)) {
                if (topicItems.size() >= maxCount) {
                    break;
                }
                topicItems.add(new SuggestionItem(suggestion, SuggestionCategory.RELATED_TOPIC, 0.95d, "TOPIC"));
            }
            if (topicItems.size() >= maxCount) {
                break;
            }
        }
        return topicItems;
    }

    private List<String> extractTopics(SuggestionContext context) {
        if (context == null) {
            return List.of();
        }
        StringBuilder combined = new StringBuilder();
        if (context.getUserMessage() != null) {
            combined.append(context.getUserMessage()).append(" ");
        }
        if (context.getAnswer() != null) {
            combined.append(context.getAnswer()).append(" ");
        }
        String text = combined.toString().trim();
        if (text.isBlank()) {
            return List.of();
        }

        List<String> topics = new ArrayList<>();
        addUniqueTopic(topics, parsePattern(text, AD_HOC_REPORT_PATTERN, "Ad Hoc Report"));
        addUniqueTopic(topics, parsePattern(text, INFORMATION_BROWSER_PATTERN, "Information Browser"));
        addUniqueTopic(topics, parsePattern(text, VIRTUAL_FIELDS_PATTERN, "Virtual Fields"));
        addUniqueTopic(topics, parsePattern(text, USERS_ROLES_PATTERN, "Users and Roles"));
        addUniqueTopic(topics, parsePattern(text, METADATA_PATTERN, "Metadata"));

        Matcher programMatcher = PROGRAM_PATTERN.matcher(text);
        while (programMatcher.find()) {
            addUniqueTopic(topics, programMatcher.group().toUpperCase(Locale.ROOT));
        }
        return topics;
    }

    private String parsePattern(String text, Pattern pattern, String canonicalTopic) {
        if (text == null || pattern == null) {
            return null;
        }
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return canonicalTopic;
        }
        return null;
    }

    private void addUniqueTopic(List<String> topics, String topic) {
        if (topic == null || topic.isBlank()) {
            return;
        }
        String normalized = topic.trim();
        for (String existing : topics) {
            if (existing.equalsIgnoreCase(normalized)) {
                return;
            }
        }
        topics.add(normalized);
    }

    private List<String> buildSuggestionsForTopic(String topic) {
        if (topic == null || topic.isBlank()) {
            return List.of();
        }
        String lower = topic.toLowerCase(Locale.ROOT);
        if (lower.equals("ad hoc report")) {
            return List.of(
                    "How do I create an Ad Hoc report?",
                    "How do I preview results before generating an Ad Hoc report?",
                    "How are Ad Hoc reports linked to users and roles?"
            );
        }
        if (lower.contains("ad hoc report designer")) {
            return List.of(
                    "How do I use the Ad Hoc Report Designer to build a report?",
                    "What are the advantages of using AHS112 for Ad Hoc reports?"
            );
        }
        if (lower.equals("information browser")) {
            return List.of(
                    "How do I use CMS100 to configure Ad Hoc reports?",
                    "What is the role of Information Browser in Ad Hoc reporting?"
            );
        }
        if (lower.equals("virtual fields")) {
            return List.of(
                    "How do virtual fields work in Ad Hoc reports?",
                    "When should I use virtual fields in an Ad Hoc report?"
            );
        }
        if (lower.equals("users and roles")) {
            return List.of(
                    "How are Ad Hoc reports linked to users and roles?",
                    "Can I override Ad Hoc report settings for individual users?"
            );
        }
        if (lower.equals("metadata")) {
            return List.of(
                    "What metadata is automatically created when an Ad Hoc report is saved?",
                    "How can metadata values be used in Ad Hoc report configuration?"
            );
        }
        if (PROGRAM_PATTERN.matcher(topic).matches()) {
            return List.of(
                    "What is " + topic + " used for?",
                    "How does " + topic + " fit into Ad Hoc reporting?"
            );
        }
        return List.of(
                "How does " + topic + " relate to the current Ad Hoc report topic?",
                "What should I know about " + topic + " when working with Ad Hoc reports?"
        );
    }

    private List<SuggestionItem> buildRuleItems(String latestUserMessage, int maxCount) {
        List<String> suggestions = suggestionRuleService.suggest(latestUserMessage, maxCount);
        return suggestions.stream()
                .map(text -> new SuggestionItem(text, SuggestionCategory.RELATED_TOPIC, 0.9d, "RULE"))
                .collect(Collectors.toList());
    }

    private List<SuggestionItem> buildGenericItems(int maxCount) {
        return suggestionRuleService.genericSuggestions(maxCount).stream()
                .map(text -> new SuggestionItem(text, SuggestionCategory.GENERIC, 0.45d, "GENERIC"))
                .collect(Collectors.toList());
    }

    private List<SuggestionItem> getLlmSuggestions(SuggestionContext context, int maxCount) {
        String cacheKey = buildCacheKey(context);
        List<String> cached = suggestionCacheService.get(cacheKey);
        if (!cached.isEmpty()) {
            return cached.stream()
                    .map(text -> new SuggestionItem(text, SuggestionCategory.RELATED_TOPIC, 0.55d, "LLM"))
                    .collect(Collectors.toList());
        }

        List<SuggestionItem> suggestions = suggestionLLMService.suggest(context, 1, maxCount);
        if (!suggestions.isEmpty()) {
            suggestionCacheService.put(cacheKey, suggestions.stream()
                    .map(SuggestionItem::getText)
                    .collect(Collectors.toList()));
        }
        return suggestions;
    }

    private List<SuggestionItem> normalizeAndRankSuggestions(List<SuggestionItem> items, int maxCount) {
        Map<String, SuggestionItem> normalized = new LinkedHashMap<>();
        List<String> addedTexts = new ArrayList<>();
        
        for (SuggestionItem item : items) {
            SuggestionItem cleaned = normalizeSuggestionItem(item);
            if (cleaned == null || cleaned.getText() == null || cleaned.getText().isBlank()) {
                continue;
            }
            
            // Validate suggestion quality
            if (!isValidSuggestion(cleaned)) {
                log.debug("Filtered invalid suggestion: {}", cleaned.getText());
                continue;
            }
            
            String dedupeKey = cleaned.getText().trim().toLowerCase(Locale.ROOT);
            
            // Check for exact duplicate
            if (normalized.containsKey(dedupeKey)) {
                SuggestionItem existing = normalized.get(dedupeKey);
                if (cleaned.getScore() > existing.getScore()) {
                    normalized.put(dedupeKey, cleaned);
                }
                continue;
            }
            
            // Check for semantic similarity with existing suggestions
            if (hasSimilarSuggestion(cleaned.getText(), addedTexts)) {
                log.debug("Filtered duplicate-meaning suggestion: {}", cleaned.getText());
                continue;
            }
            
            normalized.put(dedupeKey, cleaned);
            addedTexts.add(cleaned.getText());
            
            if (normalized.size() >= maxCount) {
                break;
            }
        }

        return normalized.values().stream()
                .sorted(this::compareSuggestionItems)
                .limit(maxCount)
                .collect(Collectors.toList());
    }

    private SuggestionItem normalizeSuggestionItem(SuggestionItem item) {
        try {
            if (item == null || item.getText() == null) {
                return null;
            }
            String text = item.getText().trim();
            if (text.isBlank()) {
                log.debug("Normalization: skipping empty text");
                return null;
            }
            
            String clean = text.replaceAll("[\r\n]+", " ");
            if (clean.isBlank()) {
                return null;
            }
            
            clean = clean.replaceAll("[?.!]+$", "").trim();
            if (clean.isBlank()) {
                return null;
            }
            
            // Remove trailing incomplete words
            clean = clean.replaceAll("\\s+(in|of|for|with|and|to|by|from)\\s*$", "").trim();
            if (clean.isBlank()) {
                log.debug("Normalization: filtered incomplete phrase: {}", item.getText());
                return null;
            }
            
            clean = clean.replaceAll("\\s{2,}", " ");
            
            // Only truncate if truly excessive (> 120 chars), preserve context
            if (clean.length() > 120) {
                int truncatePos = clean.lastIndexOf(' ', 120);
                if (truncatePos > 40 && truncatePos > 0) {
                    clean = clean.substring(0, truncatePos).trim();
                    log.debug("Normalization: truncated text from {} to {} chars", item.getText().length(), clean.length());
                }
            }
            
            if (clean.isBlank()) {
                return null;
            }

            SuggestionCategory category = item.getCategory() == null ? SuggestionCategory.GENERIC : item.getCategory();
            String userCentric = transformToUserCentricSuggestion(clean, category);
            if (userCentric == null || userCentric.isBlank()) {
                log.debug("Normalization: transformation resulted in empty text");
                return null;
            }
            return new SuggestionItem(userCentric, category, item.getScore(), item.getSource());
        } catch (Exception e) {
            log.error("Error normalizing suggestion item: {}", item, e);
            return null;
        }
    }

    private String transformToUserCentricSuggestion(String suggestion, SuggestionCategory category) {
        try {
            if (suggestion == null || suggestion.isBlank()) {
                return suggestion;
            }
            String trimmed = suggestion.trim();
            if (trimmed.isEmpty()) {
                return suggestion;
            }
            if (trimmed.length() <= 5 || startsWithQuestionPrefix(trimmed) || startsWithActionPrefix(trimmed)) {
                return trimmed;
            }
        
            // Detect likely category from content if generic
            SuggestionCategory detectedCategory = category;
            if (category == SuggestionCategory.GENERIC || category == SuggestionCategory.RELATED_TOPIC) {
                detectedCategory = detectCategory(trimmed);
            }
            
            switch (detectedCategory) {
                case FOLLOW_UP:
                    return prefixIfMissing(trimmed, "How can I ", "how can i ");
                case TROUBLESHOOTING:
                    return prefixIfMissing(trimmed, "Troubleshoot ", "troubleshoot ");
                case BEST_PRACTICE:
                    return prefixIfMissing(trimmed, "Best practices for ", "best practices for ");
                case CONFIGURATION:
                    return prefixIfMissing(trimmed, "How to configure ", "how to configure ");
                case API_RELATED:
                    return prefixIfMissing(trimmed, "API: ", "api: ");
                case RELATED_TOPIC:
                case GENERIC:
                default:
                    // Only prefix if truly generic
                    if (detectedCategory == SuggestionCategory.GENERIC) {
                        return prefixIfMissing(trimmed, "Explore ", "explore ");
                    }
                    return trimmed;
            }
        } catch (Exception e) {
            log.error("Error transforming suggestion to user-centric format: {}", suggestion, e);
            return suggestion;
        }
    }
    
    private boolean startsWithActionPrefix(String text) {
        String lower = text.strip().toLowerCase(Locale.ROOT);
        String[] actionPrefixes = {"how", "what", "why", "where", "when", "is", "can", "troubleshoot", "api", "best", "configure", "explore"};
        for (String prefix : actionPrefixes) {
            if (lower.startsWith(prefix + " ")) {
                return true;
            }
        }
        return false;
    }
    
    private SuggestionCategory detectCategory(String suggestion) {
        String lower = suggestion.toLowerCase(Locale.ROOT);
        
        if (lower.matches(".*\\b(how|troubleshoot|error|fix|issue|problem|fail|exception)\\b.*")) {
            return SuggestionCategory.TROUBLESHOOTING;
        }
        if (lower.matches(".*\\b(configure|setup|set|create|define|initialize)\\b.*")) {
            return SuggestionCategory.CONFIGURATION;
        }
        if (lower.matches(".*\\b(api|endpoint|integration|call|request|response)\\b.*")) {
            return SuggestionCategory.API_RELATED;
        }
        if (lower.matches(".*\\b(best practice|practice|standard|recommended|optimal|efficient)\\b.*")) {
            return SuggestionCategory.BEST_PRACTICE;
        }
        if (lower.matches(".*\\b(what|which|where|related|connected)\\b.*")) {
            return SuggestionCategory.FOLLOW_UP;
        }
        return SuggestionCategory.GENERIC;
    }

    private boolean startsWithQuestionPrefix(String text) {
        String normalized = text.strip().toLowerCase(Locale.ROOT);
        return normalized.startsWith("how") || normalized.startsWith("what") || normalized.startsWith("why") 
            || normalized.startsWith("where") || normalized.startsWith("when") || normalized.startsWith("is ") 
            || normalized.startsWith("can ") || normalized.startsWith("troubleshoot") || normalized.startsWith("best");
    }

    private String prefixIfMissing(String text, String prefix, String prefixLower) {
        String trimmed = text.trim();
        if (trimmed.toLowerCase(Locale.ROOT).startsWith(prefixLower)) {
            return trimmed;
        }
        return prefix + trimmed;
    }

    private boolean isValidSuggestion(SuggestionItem item) {
        try {
            if (item == null || item.getText() == null) {
                return false;
            }
            String text = item.getText().trim();
            if (text.isBlank()) {
                return false;
            }
            
            // Minimum 4 words and 15 characters for meaningful suggestion
            String[] words = text.split("\\s+");
            int wordCount = words.length;
            
            if (text.length() < 15 || wordCount < 4) {
                log.debug("Validation: rejecting short suggestion (chars={}, words={}): {}", text.length(), wordCount, text);
                return false;
            }
            
            // Reject URLs, emails
            String lowerText = text.toLowerCase(Locale.ROOT);
            if (lowerText.contains("http") || text.contains("@")) {
                return false;
            }
            
            // Reject if ends with incomplete phrase markers
            if (lowerText.matches(".*\\b(in|of|for|with|and|to|by|from)\\s*$")) {
                log.debug("Validation: rejecting incomplete phrase ending: {}", text);
                return false;
            }
            
            // Reject if ends with "is", "can be", "are" (incomplete patterns)
            if (lowerText.matches(".*\\b(is|can be|are|have|has|being)\\s*$")) {
                log.debug("Validation: rejecting incomplete verb ending: {}", text);
                return false;
            }
            
            // Reject obvious template patterns
            if (lowerText.contains("click here") || lowerText.contains("select") || lowerText.contains("choose")) {
                // Allow only if it's part of a larger instruction
                if (wordCount < 5) {
                    return false;
                }
            }
            
            // Reject overly generic single-topic suggestions
            if (isGenericTopic(text)) {
                log.debug("Validation: rejecting generic topic: {}", text);
                return false;
            }
            
            return true;
        } catch (Exception e) {
            log.error("Error validating suggestion: {}", item, e);
            return false;
        }
    }
    
    private boolean isGenericTopic(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        // Single word or generic patterns without actionable verbs
        String[] genericPatterns = {
                "^(explore|understand|learn about)\\s+\\w+\\s*(process|information|details)?$",
                "^\\w+\\s+(process|information|overview|guide)$",
                "^(customer|purchase|order|sales)\\s+(order|process|management)$"
        };
        
        for (String pattern : genericPatterns) {
            if (lower.matches(pattern)) {
                return true;
            }
        }
        return false;
    }
    
    private boolean hasSimilarSuggestion(String newText, List<String> existingTexts) {
        try {
            if (newText == null || newText.isBlank() || existingTexts == null || existingTexts.isEmpty()) {
                return false;
            }
            String normalized = normalizeForComparison(newText);
            if (normalized.isBlank()) {
                return false;
            }
            for (String existing : existingTexts) {
                if (existing == null || existing.isBlank()) {
                    continue;
                }
                String existingNormalized = normalizeForComparison(existing);
                if (existingNormalized.isBlank()) {
                    continue;
                }
                double similarity = calculateSimilarity(normalized, existingNormalized);
                if (similarity > 0.75) {
                    log.debug("Deduplication: found similar suggestion (similarity={}) - filtered: {}", String.format("%.2f", similarity), newText);
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            log.error("Error checking for similar suggestions: {}", newText, e);
            return false;
        }
    }
    
    private String normalizeForComparison(String text) {
        try {
            if (text == null || text.isBlank()) {
                return "";
            }
            return text.toLowerCase(Locale.ROOT)
                    .replaceAll("(?i)(how to|how do i|how can i|what is|explore)", "")
                    .replaceAll("(?i)(in|to|for|with|and|or)", "")
                    .replaceAll("[^a-z0-9\\s]", "")
                    .replaceAll("\\s+", " ")
                    .trim();
        } catch (Exception e) {
            log.error("Error normalizing text for comparison: {}", text, e);
            return "";
        }
    }
    
    private double calculateSimilarity(String str1, String str2) {
        try {
            if (str1 == null || str1.isBlank() || str2 == null || str2.isBlank()) {
                return str1 == null ? (str2 == null ? 1.0 : 0.0) : 0.0;
            }
            if (str1.equals(str2)) {
                return 1.0;
            }
            
            int distance = levenshteinDistance(str1, str2);
            int maxLength = Math.max(str1.length(), str2.length());
            if (maxLength == 0) {
                return 1.0;
            }
            return 1.0 - ((double) distance / maxLength);
        } catch (Exception e) {
            log.error("Error calculating similarity between '{}' and '{}'", str1, str2, e);
            return 0.0;
        }
    }
    
    private int levenshteinDistance(String s1, String s2) {
        try {
            if (s1 == null || s1.isBlank() || s2 == null || s2.isBlank()) {
                return Math.abs((s1 == null ? 0 : s1.length()) - (s2 == null ? 0 : s2.length()));
            }
            
            // Initialize distance array for s2
            int[] distances = new int[s2.length() + 1];
            for (int k = 0; k <= s2.length(); k++) {
                distances[k] = k;
            }
            
            // Compute distances for each character in s1
            for (int i = 1; i <= s1.length(); i++) {
                int[] newDistances = new int[s2.length() + 1];
                newDistances[0] = i;
                for (int j = 1; j <= s2.length(); j++) {
                    // Safe character access with bounds checking
                    if (i - 1 >= 0 && i - 1 < s1.length() && j - 1 >= 0 && j - 1 < s2.length()) {
                        int cost = s1.charAt(i - 1) == s2.charAt(j - 1) ? 0 : 1;
                        newDistances[j] = Math.min(
                                Math.min(newDistances[j - 1] + 1, distances[j] + 1),
                                distances[j - 1] + cost
                        );
                    }
                }
                distances = newDistances;
            }
            return distances[s2.length()];
        } catch (Exception e) {
            log.error("Error calculating Levenshtein distance between '{}' and '{}'", s1, s2, e);
            return Integer.MAX_VALUE / 2;
        }
    }

    private int compareSuggestionItems(SuggestionItem first, SuggestionItem second) {
        int byScore = Double.compare(second.getScore(), first.getScore());
        if (byScore != 0) {
            return byScore;
        }
        return Integer.compare(getSourcePriority(first.getSource()), getSourcePriority(second.getSource()));
    }

    private int getSourcePriority(String source) {
        if (source == null) {
            return 99;
        }
        return switch (source.toUpperCase(Locale.ROOT)) {
            case "TOPIC" -> 5;
            case "RULE" -> 10;
            case "LLM" -> 20;
            case "GENERIC" -> 30;
            default -> 99;
        };
    }

    private String buildCacheKey(SuggestionContext context) {
        return normalizeToken(context.getTenantCode()) + "|"
                + normalizeToken(context.getUserId()) + "|"
                + normalizeToken(context.getSessionId()) + "|"
                + normalizeToken(context.getUserMessage()) + "|"
                + normalizeToken(context.getAnswer());
    }

    private String normalizeToken(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private SuggestionResult mapToResult(List<SuggestionItem> items) {
        List<String> suggestions = items.stream()
                .map(SuggestionItem::getText)
                .collect(Collectors.toList());
        List<com.ai.openai_api_service.model.SuggestionDto> details = items.stream()
                .map(item -> new com.ai.openai_api_service.model.SuggestionDto(
                        item.getText(),
                        item.getSource(),
                        item.getCategory(),
                        item.getScore()
                ))
                .collect(Collectors.toList());
        return new SuggestionResult(suggestions, details);
    }
}
