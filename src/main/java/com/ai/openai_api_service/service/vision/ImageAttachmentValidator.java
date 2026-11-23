package com.ai.openai_api_service.service.vision;

import com.ai.openai_api_service.exception.OpenAIException;
import com.ai.openai_api_service.model.ChatRequest;
import com.ai.openai_api_service.model.ImageAttachmentRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Validates optional widget image fields on {@link ChatRequest}.
 * Never logs Base64 or image bytes.
 *
 * <p>Accepts either legacy flat fields or {@code images[]}, not both.
 * Enforces per-image decoded/encoded limits and collection count/aggregate limits.
 * Total HTTP body size is enforced separately by {@code ChatVisionRequestSizeFilter}.
 */
@Component
public class ImageAttachmentValidator {

    private static final Logger log = LoggerFactory.getLogger(ImageAttachmentValidator.class);

    private static final Set<String> ALLOWED_MEDIA_TYPES = Set.of(
            "image/png",
            "image/jpeg",
            "image/webp"
    );

    private static final byte[] PNG_MAGIC = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };
    private static final byte[] JPEG_MAGIC = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] RIFF = new byte[]{0x52, 0x49, 0x46, 0x46};
    private static final byte[] WEBP = new byte[]{0x57, 0x45, 0x42, 0x50};

    private final long maxBytes;
    private final long maxEncodedChars;
    private final int maxImages;
    private final long maxAggregateBytes;

    /** Test helper: max-images=3, aggregate=3×maxBytes (clamped to at least maxBytes). */
    ImageAttachmentValidator(long maxBytes) {
        this(maxBytes, 3, Math.max(maxBytes, maxBytes * 3L));
    }

    @Autowired
    public ImageAttachmentValidator(
            @Value("${chat.vision.max-bytes:4194304}") long maxBytes,
            @Value("${chat.vision.max-images:3}") int maxImages,
            @Value("${chat.vision.max-aggregate-bytes:12582912}") long maxAggregateBytes
    ) {
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("chat.vision.max-bytes must be positive");
        }
        if (maxImages <= 0) {
            throw new IllegalArgumentException("chat.vision.max-images must be positive");
        }
        if (maxAggregateBytes <= 0) {
            throw new IllegalArgumentException("chat.vision.max-aggregate-bytes must be positive");
        }
        this.maxBytes = maxBytes;
        this.maxEncodedChars = VisionRequestSizeLimits.maxEncodedBase64Chars(maxBytes);
        this.maxImages = maxImages;
        this.maxAggregateBytes = maxAggregateBytes;
    }

    /**
     * @return empty list when no image fields are present; otherwise validated attachments in order
     * @throws OpenAIException with status 400 when image fields are incomplete or invalid
     */
    public List<ValidatedImageAttachment> validateAll(ChatRequest request) {
        if (request == null) {
            return List.of();
        }

        boolean hasLegacy = hasAnyLegacyField(request);
        List<ImageAttachmentRequest> images = request.getImages();
        boolean hasImagesList = images != null && !images.isEmpty();

        if (hasLegacy && hasImagesList) {
            throw badRequest(
                    "Use either images[] or legacy image fields (imageBase64/imageMediaType/imageFileName), not both."
            );
        }

        if (!hasLegacy && !hasImagesList) {
            return List.of();
        }

        List<ValidatedImageAttachment> validated;
        if (hasLegacy) {
            validated = List.of(validateOne(
                    request.getImageBase64(),
                    request.getImageMediaType(),
                    request.getImageFileName()
            ));
        } else {
            if (images.size() > maxImages) {
                throw badRequest(
                        "Too many images. Maximum allowed is " + maxImages + "."
                );
            }
            validated = new ArrayList<>(images.size());
            for (ImageAttachmentRequest item : images) {
                if (item == null) {
                    throw badRequest("images[] entries must not be null.");
                }
                validated.add(validateOne(
                        item.getImageBase64(),
                        item.getImageMediaType(),
                        item.getImageFileName()
                ));
            }
        }

        long aggregate = 0L;
        for (ValidatedImageAttachment attachment : validated) {
            aggregate += attachment.bytes().length;
            if (aggregate > maxAggregateBytes) {
                throw badRequest(
                        "Images exceed the maximum aggregate size of " + maxAggregateBytes + " bytes."
                );
            }
        }

        log.info(
                "Validated image attachments count={} aggregateDecodedBytes={}",
                validated.size(),
                aggregate
        );
        return Collections.unmodifiableList(validated);
    }

    /**
     * @deprecated Prefer {@link #validateAll(ChatRequest)}; kept for single-field unit tests.
     */
    public java.util.Optional<ValidatedImageAttachment> validateOptional(ChatRequest request) {
        List<ValidatedImageAttachment> all = validateAll(request);
        if (all.isEmpty()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(all.get(0));
    }

    /**
     * @deprecated Prefer {@link #validateAll(ChatRequest)}; kept for single-field unit tests.
     */
    public java.util.Optional<ValidatedImageAttachment> validateOptional(
            String imageBase64,
            String imageMediaType,
            String imageFileName
    ) {
        boolean hasBase64 = isPresent(imageBase64);
        boolean hasMediaType = isPresent(imageMediaType);
        boolean hasFileName = isPresent(imageFileName);
        if (!hasBase64 && !hasMediaType && !hasFileName) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(validateOne(imageBase64, imageMediaType, imageFileName));
    }

    private ValidatedImageAttachment validateOne(
            String imageBase64,
            String imageMediaType,
            String imageFileName
    ) {
        boolean hasBase64 = isPresent(imageBase64);
        boolean hasMediaType = isPresent(imageMediaType);
        boolean hasFileName = isPresent(imageFileName);

        if (!hasBase64 || !hasMediaType || !hasFileName) {
            throw badRequest(
                    "Incomplete image payload. When attaching an image, imageBase64, "
                            + "imageMediaType, and imageFileName are all required."
            );
        }

        String trimmedBase64 = imageBase64.trim();
        if (trimmedBase64.regionMatches(true, 0, "data:", 0, 5)) {
            throw badRequest("imageBase64 must be raw Base64 without a data URL prefix.");
        }

        if (trimmedBase64.length() > maxEncodedChars) {
            throw badRequest(
                    "imageBase64 exceeds the maximum allowed encoded length of "
                            + maxEncodedChars
                            + " characters."
            );
        }

        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(trimmedBase64);
        } catch (IllegalArgumentException ex) {
            throw badRequest("imageBase64 is not valid Base64.");
        }

        if (bytes.length == 0) {
            throw badRequest("imageBase64 decoded to an empty image.");
        }
        if (bytes.length > maxBytes) {
            throw badRequest(
                    "Image exceeds the maximum allowed size of " + maxBytes + " bytes."
            );
        }

        String normalizedMime = normalizeMediaType(imageMediaType);
        if (!ALLOWED_MEDIA_TYPES.contains(normalizedMime)) {
            throw badRequest(
                    "Unsupported imageMediaType. Allowed types: image/png, image/jpeg, image/webp."
            );
        }

        String detected = detectMediaType(bytes);
        if (detected == null) {
            throw badRequest("Image bytes are not a valid PNG, JPEG, or WebP image.");
        }
        if (!detected.equals(normalizedMime)) {
            throw badRequest(
                    "imageMediaType does not match the detected image format."
            );
        }

        String fileName = imageFileName.trim();
        log.info(
                "Validated image attachment mediaType={} decodedBytes={} fileNamePresent={}",
                normalizedMime,
                bytes.length,
                !fileName.isEmpty()
        );

        return new ValidatedImageAttachment(bytes, normalizedMime, fileName);
    }

    private static boolean hasAnyLegacyField(ChatRequest request) {
        return isPresent(request.getImageBase64())
                || isPresent(request.getImageMediaType())
                || isPresent(request.getImageFileName());
    }

    static String normalizeMediaType(String mediaType) {
        String normalized = mediaType.trim().toLowerCase(Locale.ROOT);
        if ("image/jpg".equals(normalized)) {
            return "image/jpeg";
        }
        return normalized;
    }

    static String detectMediaType(byte[] bytes) {
        if (startsWith(bytes, PNG_MAGIC)) {
            return "image/png";
        }
        if (startsWith(bytes, JPEG_MAGIC)) {
            return "image/jpeg";
        }
        if (isWebp(bytes)) {
            return "image/webp";
        }
        return null;
    }

    private static boolean isWebp(byte[] bytes) {
        if (bytes.length < 12) {
            return false;
        }
        if (!startsWith(bytes, RIFF)) {
            return false;
        }
        return bytes[8] == WEBP[0]
                && bytes[9] == WEBP[1]
                && bytes[10] == WEBP[2]
                && bytes[11] == WEBP[3];
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (bytes[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }

    private static OpenAIException badRequest(String message) {
        return new OpenAIException(message, 400);
    }
}
