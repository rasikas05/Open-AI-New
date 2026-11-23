package com.ai.openai_api_service.model;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One image in {@link ChatRequest#getImages()}. Raw Base64 only (no data: URL prefix).
 */
public class ImageAttachmentRequest {

    @Schema(
            description = "Raw Base64 image bytes (no data: URL prefix).",
            example = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="
    )
    private String imageBase64;

    @Schema(
            description = "Image MIME type. Allowed: image/png, image/jpeg, image/webp.",
            example = "image/png"
    )
    private String imageMediaType;

    @Schema(description = "Original file name (metadata only)", example = "screen.png")
    private String imageFileName;

    public ImageAttachmentRequest() {
    }

    public ImageAttachmentRequest(String imageBase64, String imageMediaType, String imageFileName) {
        this.imageBase64 = imageBase64;
        this.imageMediaType = imageMediaType;
        this.imageFileName = imageFileName;
    }

    public String getImageBase64() {
        return imageBase64;
    }

    public void setImageBase64(String imageBase64) {
        this.imageBase64 = imageBase64;
    }

    public String getImageMediaType() {
        return imageMediaType;
    }

    public void setImageMediaType(String imageMediaType) {
        this.imageMediaType = imageMediaType;
    }

    public String getImageFileName() {
        return imageFileName;
    }

    public void setImageFileName(String imageFileName) {
        this.imageFileName = imageFileName;
    }
}
