package com.ai.openai_api_service.model;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Display-only history row for widget UI (comprehend history API).
 * Not used for OpenAI prompt construction — that continues to use {@link MessageDto}.
 */
public class HistoryMessageDto {

    @Schema(example = "assistant", allowableValues = {"user", "assistant", "system"})
    private String role;

    private String content;
    private Boolean sanitizedFlag;
    private String actionTaken;

    @Schema(description = "request_logs.id for assistant turns; null for user turns")
    private Long requestLogId;

    public HistoryMessageDto() {
    }

    public HistoryMessageDto(
            String role,
            String content,
            Boolean sanitizedFlag,
            String actionTaken,
            Long requestLogId
    ) {
        this.role = role;
        this.content = content;
        this.sanitizedFlag = sanitizedFlag;
        this.actionTaken = actionTaken;
        this.requestLogId = requestLogId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Boolean getSanitizedFlag() {
        return sanitizedFlag;
    }

    public void setSanitizedFlag(Boolean sanitizedFlag) {
        this.sanitizedFlag = sanitizedFlag;
    }

    public String getActionTaken() {
        return actionTaken;
    }

    public void setActionTaken(String actionTaken) {
        this.actionTaken = actionTaken;
    }

    public Long getRequestLogId() {
        return requestLogId;
    }

    public void setRequestLogId(Long requestLogId) {
        this.requestLogId = requestLogId;
    }
}
