package com.ai.openai_api_service.service.vision;

import com.ai.openai_api_service.exception.OpenAIException;
import com.ai.openai_api_service.model.ChatRequest;
import com.ai.openai_api_service.model.ImageAttachmentRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageAttachmentValidatorTest {

    private static final long DEFAULT_MAX = 4194304L;

    /** Minimal valid 1x1 PNG. */
    private static final byte[] PNG_BYTES = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="
    );

    /** Minimal JPEG SOI + APP0-ish stub that starts with FF D8 FF. */
    private static final byte[] JPEG_BYTES = new byte[]{
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xD9
    };

    /** Minimal WebP RIFF header (not a full file; signature only). */
    private static final byte[] WEBP_BYTES = new byte[]{
            0x52, 0x49, 0x46, 0x46,
            0x08, 0x00, 0x00, 0x00,
            0x57, 0x45, 0x42, 0x50
    };

    private ImageAttachmentValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ImageAttachmentValidator(DEFAULT_MAX);
    }

    @Test
    void textOnlyRequestReturnsEmpty() {
        ChatRequest request = new ChatRequest();
        request.setUserMessage("how to create customer");

        Optional<ValidatedImageAttachment> result = validator.validateOptional(request);

        assertTrue(result.isEmpty());
    }

    @Test
    void validPngWithTextReturnsAttachment() {
        String base64 = Base64.getEncoder().encodeToString(PNG_BYTES);
        ChatRequest request = imageRequest(base64, "image/png", "dot.png");
        request.setUserMessage("What is on this screen?");

        Optional<ValidatedImageAttachment> result = validator.validateOptional(request);

        assertTrue(result.isPresent());
        assertEquals("image/png", result.get().mediaType());
        assertEquals("dot.png", result.get().fileName());
        assertArrayEquals(PNG_BYTES, result.get().bytes());
        assertFalse(exceptionMessageWouldLeakBase64(
                new OpenAIException("ok", 400).getMessage(), base64));
    }

    @Test
    void imageJpgAliasNormalizesToJpeg() {
        String base64 = Base64.getEncoder().encodeToString(JPEG_BYTES);

        Optional<ValidatedImageAttachment> result = validator.validateOptional(
                base64, "image/jpg", "photo.jpg");

        assertTrue(result.isPresent());
        assertEquals("image/jpeg", result.get().mediaType());
    }

    @Test
    void validWebpAccepted() {
        String base64 = Base64.getEncoder().encodeToString(WEBP_BYTES);

        Optional<ValidatedImageAttachment> result = validator.validateOptional(
                base64, "image/webp", "shot.webp");

        assertTrue(result.isPresent());
        assertEquals("image/webp", result.get().mediaType());
    }

    @Test
    void malformedBase64Rejected() {
        OpenAIException ex = assertThrows(
                OpenAIException.class,
                () -> validator.validateOptional("%%%not-base64%%%", "image/png", "x.png")
        );

        assertEquals(400, ex.getStatusCode());
        assertTrue(ex.getMessage().toLowerCase().contains("base64"));
        assertFalse(ex.getMessage().contains("%%%not-base64%%%"));
    }

    @Test
    void dataUrlPrefixRejected() {
        String dataUrl = "data:image/png;base64," + Base64.getEncoder().encodeToString(PNG_BYTES);

        OpenAIException ex = assertThrows(
                OpenAIException.class,
                () -> validator.validateOptional(dataUrl, "image/png", "x.png")
        );

        assertEquals(400, ex.getStatusCode());
        assertTrue(ex.getMessage().toLowerCase().contains("data url")
                || ex.getMessage().toLowerCase().contains("raw"));
        assertFalse(ex.getMessage().contains(dataUrl));
    }

    @Test
    void unsupportedMediaTypeRejected() {
        String base64 = Base64.getEncoder().encodeToString(PNG_BYTES);

        OpenAIException ex = assertThrows(
                OpenAIException.class,
                () -> validator.validateOptional(base64, "image/gif", "x.gif")
        );

        assertEquals(400, ex.getStatusCode());
        assertTrue(ex.getMessage().toLowerCase().contains("unsupported"));
        assertFalse(ex.getMessage().contains(base64));
    }

    @Test
    void incompleteFieldsRejected() {
        String base64 = Base64.getEncoder().encodeToString(PNG_BYTES);

        OpenAIException missingMime = assertThrows(
                OpenAIException.class,
                () -> validator.validateOptional(base64, null, "x.png")
        );
        OpenAIException missingName = assertThrows(
                OpenAIException.class,
                () -> validator.validateOptional(base64, "image/png", "  ")
        );
        OpenAIException mimeOnly = assertThrows(
                OpenAIException.class,
                () -> validator.validateOptional(null, "image/png", null)
        );

        assertEquals(400, missingMime.getStatusCode());
        assertEquals(400, missingName.getStatusCode());
        assertEquals(400, mimeOnly.getStatusCode());
        assertTrue(missingMime.getMessage().toLowerCase().contains("incomplete"));
    }

    @Test
    void nonImageBytesRejected() {
        String base64 = Base64.getEncoder().encodeToString("hello-not-an-image".getBytes());

        OpenAIException ex = assertThrows(
                OpenAIException.class,
                () -> validator.validateOptional(base64, "image/png", "x.png")
        );

        assertEquals(400, ex.getStatusCode());
        assertFalse(ex.getMessage().contains(base64));
    }

    @Test
    void mimeMismatchRejected() {
        String base64 = Base64.getEncoder().encodeToString(PNG_BYTES);

        OpenAIException ex = assertThrows(
                OpenAIException.class,
                () -> validator.validateOptional(base64, "image/jpeg", "x.jpg")
        );

        assertEquals(400, ex.getStatusCode());
        assertTrue(ex.getMessage().toLowerCase().contains("match"));
        assertFalse(ex.getMessage().contains(base64));
    }

    @Test
    void oversizedDecodedBytesRejected() {
        ImageAttachmentValidator tiny = new ImageAttachmentValidator(4);
        String base64 = Base64.getEncoder().encodeToString(PNG_BYTES);

        OpenAIException ex = assertThrows(
                OpenAIException.class,
                () -> tiny.validateOptional(base64, "image/png", "x.png")
        );

        assertEquals(400, ex.getStatusCode());
        assertTrue(ex.getMessage().toLowerCase().contains("maximum"));
        assertFalse(ex.getMessage().contains(base64));
    }

    @Test
    void encodedBase64AtExactLimitAcceptedWhenImageValid() {
        // 3 decoded bytes → encoded max = 4 chars; JPEG magic is exactly 3 bytes.
        byte[] jpeg = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
        String base64 = Base64.getEncoder().encodeToString(jpeg);
        assertEquals(4, base64.length());
        ImageAttachmentValidator limited = new ImageAttachmentValidator(3);

        Optional<ValidatedImageAttachment> result = limited.validateOptional(
                base64, "image/jpeg", "x.jpg");

        assertTrue(result.isPresent());
        assertArrayEquals(jpeg, result.get().bytes());
    }

    @Test
    void encodedBase64OneCharOverLimitRejectedBeforeDecode() {
        ImageAttachmentValidator limited = new ImageAttachmentValidator(3);
        String tooLong = "AAAAA"; // 5 > encoded max 4; never echo in error

        OpenAIException ex = assertThrows(
                OpenAIException.class,
                () -> limited.validateOptional(tooLong, "image/png", "x.png")
        );

        assertEquals(400, ex.getStatusCode());
        assertTrue(ex.getMessage().toLowerCase().contains("encoded"));
        assertFalse(ex.getMessage().contains(tooLong));
    }

    @Test
    void decodedBytesAtExactLimitAccepted() {
        byte[] exact = new byte[PNG_BYTES.length];
        System.arraycopy(PNG_BYTES, 0, exact, 0, PNG_BYTES.length);
        String base64 = Base64.getEncoder().encodeToString(exact);
        ImageAttachmentValidator limited = new ImageAttachmentValidator(exact.length);

        Optional<ValidatedImageAttachment> result = limited.validateOptional(
                base64, "image/png", "exact.png");

        assertTrue(result.isPresent());
        assertEquals(exact.length, result.get().bytes().length);
    }

    @Test
    void decodedBytesOneByteOverLimitRejected() {
        byte[] over = new byte[PNG_BYTES.length + 1];
        System.arraycopy(PNG_BYTES, 0, over, 0, PNG_BYTES.length);
        String base64 = Base64.getEncoder().encodeToString(over);
        ImageAttachmentValidator limited = new ImageAttachmentValidator(PNG_BYTES.length);

        OpenAIException ex = assertThrows(
                OpenAIException.class,
                () -> limited.validateOptional(base64, "image/png", "over.png")
        );

        assertEquals(400, ex.getStatusCode());
        assertTrue(ex.getMessage().toLowerCase().contains("maximum"));
        assertFalse(ex.getMessage().contains(base64));
    }

    @Test
    void emptyDecodedBase64RejectedWhenFieldsPresent() {
        String emptyPayload = Base64.getEncoder().encodeToString(new byte[0]);

        OpenAIException ex = assertThrows(
                OpenAIException.class,
                () -> validator.validateOptional(emptyPayload, "image/png", "x.png")
        );

        assertEquals(400, ex.getStatusCode());
    }

    @Test
    void validateAll_textOnlyReturnsEmptyList() {
        ChatRequest request = new ChatRequest();
        request.setUserMessage("how to create customer");
        assertTrue(validator.validateAll(request).isEmpty());
    }

    @Test
    void validateAll_emptyImagesArrayTreatedAsNoImage() {
        ChatRequest request = new ChatRequest();
        request.setUserMessage("hi");
        request.setImages(List.of());
        assertTrue(validator.validateAll(request).isEmpty());
    }

    @Test
    void validateAll_legacySingleNormalizesToOneElementList() {
        String base64 = Base64.getEncoder().encodeToString(PNG_BYTES);
        List<ValidatedImageAttachment> result = validator.validateAll(
                imageRequest(base64, "image/png", "dot.png"));
        assertEquals(1, result.size());
        assertEquals("image/png", result.get(0).mediaType());
    }

    @Test
    void validateAll_imagesArrayPreservesOrder() {
        String png = Base64.getEncoder().encodeToString(PNG_BYTES);
        String jpeg = Base64.getEncoder().encodeToString(JPEG_BYTES);
        ChatRequest request = new ChatRequest();
        request.setImages(List.of(
                new ImageAttachmentRequest(png, "image/png", "a.png"),
                new ImageAttachmentRequest(jpeg, "image/jpeg", "b.jpg")
        ));

        List<ValidatedImageAttachment> result = validator.validateAll(request);

        assertEquals(2, result.size());
        assertEquals("image/png", result.get(0).mediaType());
        assertEquals("a.png", result.get(0).fileName());
        assertEquals("image/jpeg", result.get(1).mediaType());
        assertEquals("b.jpg", result.get(1).fileName());
    }

    @Test
    void validateAll_bothFormatsRejected() {
        String base64 = Base64.getEncoder().encodeToString(PNG_BYTES);
        ChatRequest request = imageRequest(base64, "image/png", "legacy.png");
        request.setImages(List.of(new ImageAttachmentRequest(base64, "image/png", "arr.png")));

        OpenAIException ex = assertThrows(OpenAIException.class, () -> validator.validateAll(request));

        assertEquals(400, ex.getStatusCode());
        assertTrue(ex.getMessage().toLowerCase().contains("not both")
                || ex.getMessage().toLowerCase().contains("either"));
        assertFalse(ex.getMessage().contains(base64));
    }

    @Test
    void validateAll_fourImagesRejected() {
        String base64 = Base64.getEncoder().encodeToString(PNG_BYTES);
        ChatRequest request = new ChatRequest();
        request.setImages(List.of(
                new ImageAttachmentRequest(base64, "image/png", "1.png"),
                new ImageAttachmentRequest(base64, "image/png", "2.png"),
                new ImageAttachmentRequest(base64, "image/png", "3.png"),
                new ImageAttachmentRequest(base64, "image/png", "4.png")
        ));

        OpenAIException ex = assertThrows(OpenAIException.class, () -> validator.validateAll(request));
        assertEquals(400, ex.getStatusCode());
        assertTrue(ex.getMessage().toLowerCase().contains("too many")
                || ex.getMessage().toLowerCase().contains("maximum"));
    }

    @Test
    void validateAll_aggregateOverLimitRejected() {
        ImageAttachmentValidator limited = new ImageAttachmentValidator(
                PNG_BYTES.length, 3, PNG_BYTES.length);
        String base64 = Base64.getEncoder().encodeToString(PNG_BYTES);
        ChatRequest request = new ChatRequest();
        request.setImages(List.of(
                new ImageAttachmentRequest(base64, "image/png", "1.png"),
                new ImageAttachmentRequest(base64, "image/png", "2.png")
        ));

        OpenAIException ex = assertThrows(OpenAIException.class, () -> limited.validateAll(request));
        assertEquals(400, ex.getStatusCode());
        assertTrue(ex.getMessage().toLowerCase().contains("aggregate"));
        assertFalse(ex.getMessage().contains(base64));
    }

    @Test
    void validateAll_threeImagesAtAggregateCapAccepted() {
        ImageAttachmentValidator limited = new ImageAttachmentValidator(
                PNG_BYTES.length, 3, PNG_BYTES.length * 3L);
        String base64 = Base64.getEncoder().encodeToString(PNG_BYTES);
        ChatRequest request = new ChatRequest();
        request.setImages(List.of(
                new ImageAttachmentRequest(base64, "image/png", "1.png"),
                new ImageAttachmentRequest(base64, "image/png", "2.png"),
                new ImageAttachmentRequest(base64, "image/png", "3.png")
        ));

        List<ValidatedImageAttachment> result = limited.validateAll(request);
        assertEquals(3, result.size());
    }

    private static ChatRequest imageRequest(String base64, String mime, String fileName) {
        ChatRequest request = new ChatRequest();
        request.setImageBase64(base64);
        request.setImageMediaType(mime);
        request.setImageFileName(fileName);
        return request;
    }

    private static boolean exceptionMessageWouldLeakBase64(String message, String base64) {
        return message != null && base64 != null && message.contains(base64);
    }
}
