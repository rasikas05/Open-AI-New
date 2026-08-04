package com.ai.openai_api_service.model;

/**
 * Public audit descriptor for a business placeholder (7B.3). No raw identifier values.
 */
public class BusinessProtectedEntityDto {

    private String type;
    private String placeholder;

    public BusinessProtectedEntityDto() {
    }

    public BusinessProtectedEntityDto(String type, String placeholder) {
        this.type = type;
        this.placeholder = placeholder;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getPlaceholder() {
        return placeholder;
    }

    public void setPlaceholder(String placeholder) {
        this.placeholder = placeholder;
    }
}
