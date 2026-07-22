package com.ai.openai_api_service.model;

/**
 * Optional widget report after M3 MI execution (pagination cursor).
 */
public class M3ClientReportDto {

    private String searchContextId;
    private String positionkey;
    private Integer recordCount;
    private Boolean hasMore;

    public String getSearchContextId() {
        return searchContextId;
    }

    public void setSearchContextId(String searchContextId) {
        this.searchContextId = searchContextId;
    }

    public String getPositionkey() {
        return positionkey;
    }

    public void setPositionkey(String positionkey) {
        this.positionkey = positionkey;
    }

    public Integer getRecordCount() {
        return recordCount;
    }

    public void setRecordCount(Integer recordCount) {
        this.recordCount = recordCount;
    }

    public Boolean getHasMore() {
        return hasMore;
    }

    public void setHasMore(Boolean hasMore) {
        this.hasMore = hasMore;
    }
}
