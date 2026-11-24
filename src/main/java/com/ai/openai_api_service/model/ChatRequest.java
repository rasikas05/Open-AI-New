package com.ai.openai_api_service.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public class ChatRequest {

    @NotBlank(message = "tenantCode is required")
    @Schema(example = "infor")
    private String tenantCode;

    @NotBlank(message = "userId is required")
    @Schema(example = "rasika")
    private String userId;

    @NotBlank(message = "sessionId is required")
    @Schema(example = "session-001")
    private String sessionId;

    @NotBlank(message = "userMessage is required")
    @Schema(example = "String")
    private String userMessage;

    @Schema(
            description = "Optional routing mode. Omit or auto = existing Python route. "
                    + "m3 forces live pipeline; docs forces documentation/RAG. Lowercase only.",
            allowableValues = {"auto", "m3", "docs"},
            example = "auto"
    )
    private ChatMode mode;

    @Schema(
            description = "When set, this turn edits the given request_logs.id. "
                    + "Must be the latest active turn in the session. Creates a new request row "
                    + "and supersedes the old one.",
            example = "123"
    )
    private Long editOfRequestLogId;

    @Schema(
            description = "Docs mode only. When true (default), insufficient RAG may use general GPT "
                    + "and PARTIAL may use gap-fill. When false, answers come only from retrieved documents. "
                    + "Ignored for Auto and Live M3.",
            example = "true"
    )
    private Boolean externalSourceEnabled;

    @Valid
    @Schema(example = "[]")
    private List<MessageDto> history;

    @Valid
    @Schema(description = "Optional M3 MI execution report from widget (pagination cursor)")
    private M3ClientReportDto m3ClientReport;

    @Schema(
            description = "Legacy single-image Base64 (no data: URL prefix). "
                    + "Use either these flat fields or images[], not both. "
                    + "When any flat image field is set, imageBase64, imageMediaType, and imageFileName are all required."
    )
    private String imageBase64;

    @Schema(
            description = "Legacy single-image MIME type. Allowed: image/png, image/jpeg, image/webp.",
            example = "image/png"
    )
    private String imageMediaType;

    @Schema(description = "Legacy single-image file name (metadata only)", example = "screen.png")
    private String imageFileName;

    @Valid
    @Schema(
            description = "Preferred multi-image attachments (max configured by chat.vision.max-images). "
                    + "Omit for text-only. Do not combine with legacy flat image fields."
    )
    private List<ImageAttachmentRequest> images;

    @Schema(
            description = "Server-issued message id from POST /api/chat/prepare. "
                    + "Required when images or IDM attachments are present."
    )
    private String messageId;

    @Valid
    @Schema(
            description = "IDM attachment metadata (PIDs) from widget upload. "
                    + "0–3 items; no Base64. Correlate to images[] by imageOrder."
    )
    private List<ChatAttachmentRef> attachments;

    public ChatRequest() {
    }

    public ChatRequest(String userMessage, List<MessageDto> history) {
        this.userMessage = userMessage;
        this.history = history;
    }

    public String getTenantCode() {
        return tenantCode;
    }

    public void setTenantCode(String tenantCode) {
        this.tenantCode = tenantCode;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getUserMessage() {
        return userMessage;
    }

    public void setUserMessage(String userMessage) {
        this.userMessage = userMessage;
    }

    public ChatMode getMode() {
        return mode;
    }

    public void setMode(ChatMode mode) {
        this.mode = mode;
    }

    public Long getEditOfRequestLogId() {
        return editOfRequestLogId;
    }

    public void setEditOfRequestLogId(Long editOfRequestLogId) {
        this.editOfRequestLogId = editOfRequestLogId;
    }

    public Boolean getExternalSourceEnabled() {
        return externalSourceEnabled;
    }

    public void setExternalSourceEnabled(Boolean externalSourceEnabled) {
        this.externalSourceEnabled = externalSourceEnabled;
    }

    public List<MessageDto> getHistory() {
        return history;
    }

    public void setHistory(List<MessageDto> history) {
        this.history = history;
    }

    public M3ClientReportDto getM3ClientReport() {
        return m3ClientReport;
    }

    public void setM3ClientReport(M3ClientReportDto m3ClientReport) {
        this.m3ClientReport = m3ClientReport;
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

    public List<ImageAttachmentRequest> getImages() {
        return images;
    }

    public void setImages(List<ImageAttachmentRequest> images) {
        this.images = images;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public List<ChatAttachmentRef> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<ChatAttachmentRef> attachments) {
        this.attachments = attachments;
    }
}

