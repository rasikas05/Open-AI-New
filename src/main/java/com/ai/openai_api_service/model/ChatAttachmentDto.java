package com.ai.openai_api_service.model;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Attachment metadata returned on chat/history (no binary, no IDM temp URLs).
 */
public class ChatAttachmentDto {

    private Long attachmentId;
    private String idmPid;
    private String fileName;
    private String mimeType;
    private Long fileSize;
    private int imageOrder;

    @Schema(allowableValues = {"UPLOADED", "FAILED"})
    private String uploadStatus;

    public ChatAttachmentDto() {
    }

    public ChatAttachmentDto(
            Long attachmentId,
            String idmPid,
            String fileName,
            String mimeType,
            Long fileSize,
            int imageOrder,
            String uploadStatus
    ) {
        this.attachmentId = attachmentId;
        this.idmPid = idmPid;
        this.fileName = fileName;
        this.mimeType = mimeType;
        this.fileSize = fileSize;
        this.imageOrder = imageOrder;
        this.uploadStatus = uploadStatus;
    }

    public Long getAttachmentId() {
        return attachmentId;
    }

    public void setAttachmentId(Long attachmentId) {
        this.attachmentId = attachmentId;
    }

    public String getIdmPid() {
        return idmPid;
    }

    public void setIdmPid(String idmPid) {
        this.idmPid = idmPid;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public int getImageOrder() {
        return imageOrder;
    }

    public void setImageOrder(int imageOrder) {
        this.imageOrder = imageOrder;
    }

    public String getUploadStatus() {
        return uploadStatus;
    }

    public void setUploadStatus(String uploadStatus) {
        this.uploadStatus = uploadStatus;
    }
}
