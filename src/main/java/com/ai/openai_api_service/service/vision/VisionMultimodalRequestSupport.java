package com.ai.openai_api_service.service.vision;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Production multimodal Chat Completions body builder for vision query generation.
 * Independent of the test-only {@code BedrockVisionRequestSupport} smoke helper.
 */
public final class VisionMultimodalRequestSupport {

    private static final Set<String> ALLOWED_MEDIA_TYPES = Set.of(
            "image/png",
            "image/jpeg",
            "image/webp"
    );

    private VisionMultimodalRequestSupport() {
    }

    public static String toDataUrl(String mediaType, byte[] bytes) {
        if (mediaType == null || mediaType.isBlank()) {
            throw new IllegalArgumentException("mediaType required");
        }
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("image bytes required");
        }
        String normalized = mediaType.trim().toLowerCase(Locale.ROOT);
        if ("image/jpg".equals(normalized)) {
            normalized = "image/jpeg";
        }
        if (!ALLOWED_MEDIA_TYPES.contains(normalized)) {
            throw new IllegalArgumentException("Unsupported mediaType for multimodal request");
        }
        return "data:" + normalized + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }

    /**
     * User message content array: one text part, then each image as {@code image_url} data URL in order.
     */
    public static List<Map<String, Object>> buildUserContentParts(
            String text,
            List<ValidatedImageAttachment> images
    ) {
        if (images == null || images.isEmpty()) {
            throw new IllegalArgumentException("at least one image required");
        }
        String prompt = text == null ? "" : text;
        List<Map<String, Object>> content = new ArrayList<>(1 + images.size());
        content.add(Map.of("type", "text", "text", prompt));
        for (ValidatedImageAttachment image : images) {
            if (image == null) {
                throw new IllegalArgumentException("image attachment must not be null");
            }
            String dataUrl = toDataUrl(image.mediaType(), image.bytes());
            content.add(Map.of(
                    "type", "image_url",
                    "image_url", Map.of("url", dataUrl)
            ));
        }
        return content;
    }

    /**
     * Single-image overload for tests and thin callers.
     */
    public static List<Map<String, Object>> buildUserContentParts(String text, String mediaType, byte[] bytes) {
        return buildUserContentParts(
                text,
                List.of(new ValidatedImageAttachment(bytes, mediaType, "image"))
        );
    }

    public static Map<String, Object> buildChatCompletionBody(
            String model,
            String configuredReasoningEffort,
            int defaultMaxCompletionTokens,
            List<Map<String, Object>> messages,
            Double temperature,
            Integer maxCompletionTokens,
            boolean jsonObjectResponse
    ) {
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("model required");
        }
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("messages required");
        }

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", messages);

        if (isGpt56Family(model)) {
            String reasoningEffort = effectiveReasoningEffort(configuredReasoningEffort);
            body.put("reasoning_effort", reasoningEffort);
            int tokenBudget = maxCompletionTokens != null ? maxCompletionTokens : defaultMaxCompletionTokens;
            body.put("max_completion_tokens", tokenBudget);
            if (temperature != null && "none".equals(reasoningEffort)) {
                body.put("temperature", temperature);
            }
        } else {
            if (temperature != null) {
                body.put("temperature", temperature);
            }
            if (maxCompletionTokens != null) {
                body.put("max_tokens", maxCompletionTokens);
            }
        }
        if (jsonObjectResponse) {
            body.put("response_format", Map.of("type", "json_object"));
        }
        return body;
    }

    public static boolean isGpt56Family(String model) {
        if (model == null || model.isBlank()) {
            return false;
        }
        String normalized = model.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("global.") || normalized.startsWith("us.") || normalized.startsWith("in.")) {
            normalized = normalized.substring(normalized.indexOf('.') + 1);
        }
        if (normalized.startsWith("openai.")) {
            normalized = normalized.substring("openai.".length());
        }
        return normalized.startsWith("gpt-5");
    }

    public static String effectiveReasoningEffort(String configuredEffort) {
        if (configuredEffort == null || configuredEffort.isBlank()) {
            return "none";
        }
        return configuredEffort.trim().toLowerCase(Locale.ROOT);
    }
}
