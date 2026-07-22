package com.ai.openai_api_service.model;

/**
 * Outbound pagination hints for the widget (backward compatible optional fields).
 */
public class PaginationMetadataDto {

    private Integer pageSize;
    private Boolean supportsContinuation;
    private Boolean hasMore;

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }

    public Boolean getSupportsContinuation() {
        return supportsContinuation;
    }

    public void setSupportsContinuation(Boolean supportsContinuation) {
        this.supportsContinuation = supportsContinuation;
    }

    public Boolean getHasMore() {
        return hasMore;
    }

    public void setHasMore(Boolean hasMore) {
        this.hasMore = hasMore;
    }
}
