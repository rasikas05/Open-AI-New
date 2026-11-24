package com.ai.openai_api_service.service.vision;

import com.ai.openai_api_service.exception.OpenAIException;
import com.ai.openai_api_service.model.OpenAIUsage;
import com.ai.openai_api_service.service.LlmChatCompletionsClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bedrock/OpenAI vision → English documentation search queries.
 * Independently testable; not wired into {@code ComprehendChatService} in Phase B.
 * Never logs image bytes or Base64.
 */
@Service
public class VisionQueryGenerationService {

    private static final Logger log = LoggerFactory.getLogger(VisionQueryGenerationService.class);

    public static final String IMAGE_ONLY_SENTINEL = "[Image]";

    public static final String IMAGE_ONLY_USER_INSTRUCTION =
            "The user attached an image without a text question. "
                    + "Interpret the visible Infor M3 / CloudSuite UI or documentation cues "
                    + "and propose English documentation search queries.";

    private static final int MIN_QUERIES = 3;
    private static final int MAX_QUERIES = 4;
    private static final Pattern MARKDOWN_JSON_FENCE = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```");

    private static final String SYSTEM_PROMPT = """
            You are an Infor M3 / CloudSuite documentation search specialist with vision.

            Your job:
            1. Read all attached images together with the user's question. Consider every image; \
            do not ignore any of them.
            2. Understand the question in its original language (including English, German, Hindi, \
            Marathi, Spanish, or French). Do not change the user's intent.
            3. Produce English searchQueries for an English Infor M3 / CloudSuite documentation \
            vector index (Qdrant). Prefer exactly 4 distinct queries; never fewer than 3.
            4. Provide a brief factual imageSummary (covering all images) and any clearly readable \
            visibleText strings from any of the images.

            Strict rules:
            - Do not invent program IDs, error codes, customer numbers, order numbers, or other \
            identifiers that are not clearly readable in the images.
            - Do not answer with live tenant / MI data.
            - searchQueries must be English even when the user question is not.
            - Reply with a single JSON object only, matching:
              {"imageSummary":"string","visibleText":["string"],"searchQueries":["string",...]}
            """;

    private final LlmChatCompletionsClient llmClient;
    private final ObjectMapper objectMapper;
    private final int maxCompletionTokens;

    public VisionQueryGenerationService(
            LlmChatCompletionsClient llmClient,
            ObjectMapper objectMapper,
            @Value("${chat.vision.query-gen.max-completion-tokens:512}") int maxCompletionTokens
    ) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
        this.maxCompletionTokens = maxCompletionTokens > 0 ? maxCompletionTokens : 512;
    }

    public VisionQueryGenerationResult generate(String originalQuestion, ValidatedImageAttachment image) {
        return generate(originalQuestion, image == null ? List.of() : List.of(image));
    }

    public VisionQueryGenerationResult generate(String originalQuestion, List<ValidatedImageAttachment> images) {
        if (images == null || images.isEmpty()) {
            throw new OpenAIException("Validated image attachment is required for vision query generation.", 400);
        }
        for (ValidatedImageAttachment image : images) {
            if (image == null || image.bytes() == null || image.bytes().length == 0) {
                throw new OpenAIException("Validated image attachment is required for vision query generation.", 400);
            }
            if (image.mediaType() == null || image.mediaType().isBlank()) {
                throw new OpenAIException("Validated image mediaType is required for vision query generation.", 400);
            }
        }

        String userText = resolveUserPromptText(originalQuestion);
        List<Map<String, Object>> userContent = VisionMultimodalRequestSupport.buildUserContentParts(
                userText,
                images
        );

        List<Map<String, Object>> messages = List.of(
                Map.of("role", "system", "content", SYSTEM_PROMPT),
                Map.of("role", "user", "content", userContent)
        );

        int aggregateBytes = images.stream().mapToInt(img -> img.bytes().length).sum();
        log.info(
                "Vision query generation starting imageCount={} aggregateDecodedBytes={} promptChars={} sentinelPrompt={}",
                images.size(),
                aggregateBytes,
                userText.length(),
                isImageOnlySentinel(originalQuestion)
        );

        LlmChatCompletionsClient.ChatCompletionResult completion;
        try {
            completion = llmClient.completeMultimodal(messages, 0.2, maxCompletionTokens, true);
        } catch (OpenAIException e) {
            throw sanitizeException(e);
        }

        VisionQueryGenerationResult parsed = parseResult(completion.content(), completion.usage(), completion.elapsedMs());
        log.info(
                "Vision query generation completed queryCount={} elapsedMs={} usagePresent={}",
                parsed.searchQueries().size(),
                parsed.elapsedMs(),
                parsed.usage() != null
        );
        return parsed;
    }

    String resolveUserPromptText(String originalQuestion) {
        if (isImageOnlySentinel(originalQuestion)) {
            return IMAGE_ONLY_USER_INSTRUCTION;
        }
        return originalQuestion;
    }

    public static boolean isImageOnlySentinel(String originalQuestion) {
        if (originalQuestion == null) {
            return true;
        }
        String trimmed = originalQuestion.trim();
        return trimmed.isEmpty() || IMAGE_ONLY_SENTINEL.equals(trimmed);
    }

    VisionQueryGenerationResult parseResult(String content, OpenAIUsage usage, long elapsedMs) {
        if (content == null || content.isBlank()) {
            throw new OpenAIException("Empty vision query-generation response from model.", 502);
        }
        String cleaned = content.strip();
        Matcher matcher = MARKDOWN_JSON_FENCE.matcher(cleaned);
        if (matcher.find()) {
            cleaned = matcher.group(1).strip();
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(cleaned);
        } catch (Exception e) {
            throw new OpenAIException("Vision query-generation response is not valid JSON.", 502);
        }
        if (root == null || !root.isObject()) {
            throw new OpenAIException("Vision query-generation response is not a JSON object.", 502);
        }

        String imageSummary = readOptionalString(root, "imageSummary");
        List<String> visibleText = readOptionalStringArray(root, "visibleText");
        List<String> searchQueries = readRequiredSearchQueries(root);

        OpenAIUsage safeUsage = usage != null ? usage : new OpenAIUsage(0, 0, 0, null);
        return new VisionQueryGenerationResult(
                imageSummary,
                List.copyOf(visibleText),
                List.copyOf(searchQueries),
                safeUsage,
                elapsedMs
        );
    }

    private static String readOptionalString(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) {
            return "";
        }
        if (!node.isTextual()) {
            throw new OpenAIException("Vision query-generation JSON field '" + field + "' must be a string.", 502);
        }
        return node.asText().strip();
    }

    private static List<String> readOptionalStringArray(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            throw new OpenAIException("Vision query-generation JSON field '" + field + "' must be an array.", 502);
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (item == null || item.isNull()) {
                continue;
            }
            if (!item.isTextual()) {
                throw new OpenAIException(
                        "Vision query-generation JSON field '" + field + "' must contain only strings.",
                        502
                );
            }
            String text = item.asText().strip();
            if (!text.isEmpty()) {
                values.add(text);
            }
        }
        return values;
    }

    private static List<String> readRequiredSearchQueries(JsonNode root) {
        JsonNode node = root.get("searchQueries");
        if (node == null || node.isNull()) {
            throw new OpenAIException("Vision query-generation JSON missing searchQueries.", 502);
        }
        if (!node.isArray()) {
            throw new OpenAIException("Vision query-generation searchQueries must be an array.", 502);
        }

        LinkedHashSet<String> deduped = new LinkedHashSet<>();
        for (JsonNode item : node) {
            if (item == null || item.isNull()) {
                continue;
            }
            if (!item.isTextual()) {
                throw new OpenAIException("Vision query-generation searchQueries must contain only strings.", 502);
            }
            String query = item.asText().strip();
            if (query.isEmpty()) {
                continue;
            }
            String dedupeKey = query.toLowerCase(Locale.ROOT);
            boolean alreadyPresent = deduped.stream()
                    .anyMatch(existing -> existing.toLowerCase(Locale.ROOT).equals(dedupeKey));
            if (!alreadyPresent) {
                deduped.add(query);
            }
            if (deduped.size() >= MAX_QUERIES) {
                break;
            }
        }

        List<String> queries = new ArrayList<>(deduped);
        if (queries.size() < MIN_QUERIES) {
            throw new OpenAIException(
                    "Vision query-generation returned fewer than " + MIN_QUERIES + " usable searchQueries.",
                    502
            );
        }
        return queries;
    }

    /**
     * Ensure exception messages never echo Base64 / data URLs from multimodal payloads.
     */
    static OpenAIException sanitizeException(OpenAIException original) {
        if (original == null) {
            return new OpenAIException("Vision query generation failed.", 502);
        }
        String message = original.getMessage();
        if (message != null && (message.contains("data:image/") || looksLikeLargeBase64(message))) {
            return new OpenAIException(
                    "Vision query generation failed with a provider error.",
                    original.getStatusCode(),
                    original.getErrorCode(),
                    original.isNonRetryable(),
                    null
            );
        }
        return original;
    }

    private static boolean looksLikeLargeBase64(String message) {
        return message.length() > 200 && message.matches("(?s).*[A-Za-z0-9+/]{80,}={0,2}.*");
    }
}
