package com.ai.openai_api_service.service.vision;

/**
 * Distinct size limits for chat image requests.
 *
 * <ul>
 *   <li><b>Decoded image</b> ({@code chat.vision.max-bytes}) — per-image bytes after Base64 decode.</li>
 *   <li><b>Encoded image</b> ({@link #maxEncodedBase64Chars(long)}) — Base64 character cap per image.</li>
 *   <li><b>Aggregate decoded</b> ({@code chat.vision.max-aggregate-bytes}) — sum of decoded sizes.</li>
 *   <li><b>Total HTTP body</b> ({@link #maxHttpBodyBytes(int, long, long)}) —
 *       {@code maxImages * encoded(maxBytesPerImage) + jsonOverhead}.</li>
 * </ul>
 */
public final class VisionRequestSizeLimits {

    private VisionRequestSizeLimits() {
    }

    /**
     * Maximum Base64 character length for a decoded payload of {@code maxDecodedBytes}.
     * Formula: {@code 4 * ceil(n / 3)}.
     */
    public static long maxEncodedBase64Chars(long maxDecodedBytes) {
        if (maxDecodedBytes < 0) {
            throw new IllegalArgumentException("maxDecodedBytes must be non-negative");
        }
        return 4L * ((maxDecodedBytes + 2L) / 3L);
    }

    /**
     * Maximum HTTP request body for {@code POST /api/chat/comprehend} with up to
     * {@code maxImages} images each at most {@code maxBytesPerImage} decoded bytes,
     * plus JSON overhead for non-image fields.
     */
    public static long maxHttpBodyBytes(int maxImages, long maxBytesPerImage, long jsonOverheadBytes) {
        if (maxImages <= 0) {
            throw new IllegalArgumentException("maxImages must be positive");
        }
        if (maxBytesPerImage < 0) {
            throw new IllegalArgumentException("maxBytesPerImage must be non-negative");
        }
        if (jsonOverheadBytes < 0) {
            throw new IllegalArgumentException("jsonOverheadBytes must be non-negative");
        }
        return (long) maxImages * maxEncodedBase64Chars(maxBytesPerImage) + jsonOverheadBytes;
    }

    /**
     * @deprecated Use {@link #maxHttpBodyBytes(int, long, long)} with max-images.
     */
    @Deprecated
    public static long maxHttpBodyBytes(long maxDecodedBytes, long jsonOverheadBytes) {
        return maxHttpBodyBytes(1, maxDecodedBytes, jsonOverheadBytes);
    }
}
