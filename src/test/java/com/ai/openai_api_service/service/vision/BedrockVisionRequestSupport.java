package com.ai.openai_api_service.service.vision;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Test-only helpers for Stage 1 Bedrock multimodal vision smoke.
 * Does not change production {@code OpenAiChatRequestBuilder} or chat client contracts.
 */
final class BedrockVisionRequestSupport {

    static final String SMOKE_PHRASE = "M3-VISION-SMOKE";

    static final String DEFAULT_MODEL = "global.openai.gpt-5.6-terra";
    static final String DEFAULT_URL =
            "https://bedrock-runtime.eu-west-1.amazonaws.com/openai/v1/chat/completions";
    static final String DEFAULT_PROMPT =
            "Read all visible text in this image. Reply with only the exact text you see.";

    private BedrockVisionRequestSupport() {
    }

    static byte[] generateSmokePngBytes() throws IOException {
        BufferedImage image = new BufferedImage(420, 80, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, image.getWidth(), image.getHeight());
            g.setColor(Color.BLACK);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 28));
            g.drawString(SMOKE_PHRASE, 16, 50);
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", out)) {
            throw new IOException("ImageIO failed to encode PNG");
        }
        return out.toByteArray();
    }

    static String toPngDataUrl(byte[] pngBytes) {
        if (pngBytes == null || pngBytes.length == 0) {
            throw new IllegalArgumentException("pngBytes required");
        }
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(pngBytes);
    }

    /**
     * Multimodal Chat Completions body for GPT-5.6 family (max_completion_tokens + reasoning_effort).
     */
    static Map<String, Object> buildMultimodalBody(String model, String dataUrl, String prompt) {
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("model required");
        }
        if (dataUrl == null || !dataUrl.startsWith("data:image/png;base64,")) {
            throw new IllegalArgumentException("dataUrl must be a PNG data URL");
        }
        String text = (prompt == null || prompt.isBlank()) ? DEFAULT_PROMPT : prompt;

        List<Map<String, Object>> content = new ArrayList<>();
        content.add(Map.of("type", "text", "text", text));
        content.add(Map.of(
                "type", "image_url",
                "image_url", Map.of("url", dataUrl)
        ));

        Map<String, Object> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", content);

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", List.of(userMessage));
        if (isGpt56Family(model)) {
            body.put("reasoning_effort", "none");
            body.put("max_completion_tokens", 256);
        }
        return body;
    }

    @SuppressWarnings("unchecked")
    static String extractAssistantContent(Map<String, Object> response) {
        if (response == null) {
            throw new IllegalArgumentException("response is null");
        }
        Object choicesObj = response.get("choices");
        if (!(choicesObj instanceof List<?> choices) || choices.isEmpty()) {
            throw new IllegalArgumentException("response has no choices");
        }
        Object first = choices.get(0);
        if (!(first instanceof Map<?, ?> choice)) {
            throw new IllegalArgumentException("unexpected choice format");
        }
        Object messageObj = choice.get("message");
        if (!(messageObj instanceof Map<?, ?> message)) {
            throw new IllegalArgumentException("unexpected message format");
        }
        Object content = message.get("content");
        return content != null ? content.toString() : "";
    }

    static boolean responseMentionsSmokePhrase(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        String normalized = content.replaceAll("[\\s\\p{Punct}]+", "").toUpperCase(Locale.ROOT);
        String expected = SMOKE_PHRASE.replace("-", "").toUpperCase(Locale.ROOT);
        return normalized.contains(expected);
    }

    static boolean isGpt56Family(String model) {
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
}
