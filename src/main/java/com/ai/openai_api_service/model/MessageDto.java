package com.ai.openai_api_service.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class MessageDto {

    @NotBlank(message = "history.role is required")
    @Pattern(
            regexp = "system|user|assistant",
            message = "history.role must be one of: system, user, assistant"
    )
    @Schema(example = "user", allowableValues = {"system", "user", "assistant"})
    private String role;

    @NotBlank(message = "history.content is required")
    @Schema(example = "Summarize this in 30 words.")
    private String content;

    public MessageDto() {
    }

    public MessageDto(String role, String content) {
        this.role = role;
        this.content = content;
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
}

