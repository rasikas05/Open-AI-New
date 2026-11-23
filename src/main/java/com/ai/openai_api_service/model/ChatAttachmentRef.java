package com.ai.openai_api_service.model;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * IDM attachment metadata from the widget (no Base64).
 */
public class ChatAttachmentRef {

    @Schema(description = "IDM document PID", example = "M3_Bot_Images-2-1-LATEST")
    private String idmPid;

    @Schema(example = "machine.png")
    private String fileName;

    @Schema(example = "image/png")
    private String mimeType;

    @Schema(description = "Original file size in bytes")
    private Long fileSize;

    @Schema(description = "0-based order matching images[] index", example = "0")
    private Integer imageOrder;

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

    public Integer getImageOrder() {
        return imageOrder;
    }

    public void setImageOrder(Integer imageOrder) {
        this.imageOrder = imageOrder;
    }
}
