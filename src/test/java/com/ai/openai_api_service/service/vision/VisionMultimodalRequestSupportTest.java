package com.ai.openai_api_service.service.vision;

import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisionMultimodalRequestSupportTest {

    private static final byte[] PNG = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00
    };
    private static final byte[] JPEG = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00};
    private static final byte[] WEBP = new byte[]{
            0x52, 0x49, 0x46, 0x46, 0x08, 0x00, 0x00, 0x00, 0x57, 0x45, 0x42, 0x50
    };

    @Test
    void toDataUrl_usesNormalizedMimeForPngJpegWebp() {
        assertTrue(VisionMultimodalRequestSupport.toDataUrl("image/png", PNG)
                .startsWith("data:image/png;base64,"));
        assertTrue(VisionMultimodalRequestSupport.toDataUrl("image/jpeg", JPEG)
                .startsWith("data:image/jpeg;base64,"));
        assertTrue(VisionMultimodalRequestSupport.toDataUrl("image/jpg", JPEG)
                .startsWith("data:image/jpeg;base64,"));
        assertTrue(VisionMultimodalRequestSupport.toDataUrl("image/webp", WEBP)
                .startsWith("data:image/webp;base64,"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildUserContentParts_includesTextAndImageUrl() {
        List<Map<String, Object>> parts = VisionMultimodalRequestSupport.buildUserContentParts(
                "Was ist OIS100?",
                "image/png",
                PNG
        );
        assertEquals(2, parts.size());
        assertEquals("text", parts.get(0).get("type"));
        assertEquals("Was ist OIS100?", parts.get(0).get("text"));
        assertEquals("image_url", parts.get(1).get("type"));
        Map<String, Object> imageUrl = (Map<String, Object>) parts.get(1).get("image_url");
        String url = (String) imageUrl.get("url");
        assertTrue(url.startsWith("data:image/png;base64,"));
        assertTrue(url.endsWith(Base64.getEncoder().encodeToString(PNG)));
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildUserContentParts_includesAllImagesInOrder() {
        List<ValidatedImageAttachment> images = List.of(
                new ValidatedImageAttachment(PNG, "image/png", "a.png"),
                new ValidatedImageAttachment(JPEG, "image/jpeg", "b.jpg"),
                new ValidatedImageAttachment(WEBP, "image/webp", "c.webp")
        );
        List<Map<String, Object>> parts = VisionMultimodalRequestSupport.buildUserContentParts(
                "compare these", images);
        assertEquals(4, parts.size());
        assertEquals("compare these", parts.get(0).get("text"));
        assertTrue(((String) ((Map<String, Object>) parts.get(1).get("image_url")).get("url"))
                .startsWith("data:image/png;base64,"));
        assertTrue(((String) ((Map<String, Object>) parts.get(2).get("image_url")).get("url"))
                .startsWith("data:image/jpeg;base64,"));
        assertTrue(((String) ((Map<String, Object>) parts.get(3).get("image_url")).get("url"))
                .startsWith("data:image/webp;base64,"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildChatCompletionBody_includesGpt56AndJsonObject() {
        List<Map<String, Object>> messages = List.of(
                Map.of("role", "system", "content", "sys"),
                Map.of("role", "user", "content", VisionMultimodalRequestSupport.buildUserContentParts(
                        "q", "image/png", PNG))
        );
        Map<String, Object> body = VisionMultimodalRequestSupport.buildChatCompletionBody(
                "global.openai.gpt-5.6-terra",
                "none",
                4096,
                messages,
                0.2,
                512,
                true
        );
        assertEquals("global.openai.gpt-5.6-terra", body.get("model"));
        assertEquals("none", body.get("reasoning_effort"));
        assertEquals(512, body.get("max_completion_tokens"));
        assertEquals(Map.of("type", "json_object"), body.get("response_format"));
        assertEquals(0.2, body.get("temperature"));
        List<Map<String, Object>> outMessages = (List<Map<String, Object>>) body.get("messages");
        assertEquals(2, outMessages.size());
    }

    @Test
    void toDataUrl_rejectsEmptyBytes() {
        assertThrows(IllegalArgumentException.class,
                () -> VisionMultimodalRequestSupport.toDataUrl("image/png", new byte[0]));
    }
}
