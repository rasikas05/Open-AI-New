package com.ai.openai_api_service.m3.dto;

public class M3FunctionResponse {
    private String functionId;
    private String programName;
    private String functionGroup;
    private String functionCategory;
    private String componentGroup;
    private String authorityRequired;
    private String changedBy;
    private String changeDate;

    public M3FunctionResponse() {
    }

    public M3FunctionResponse(String functionId, String programName, String functionGroup, String functionCategory, String componentGroup, String authorityRequired, String changedBy, String changeDate) {
        this.functionId = functionId;
        this.programName = programName;
        this.functionGroup = functionGroup;
        this.functionCategory = functionCategory;
        this.componentGroup = componentGroup;
        this.authorityRequired = authorityRequired;
        this.changedBy = changedBy;
        this.changeDate = changeDate;
    }

    public String getFunctionId() {
        return functionId;
    }

    public void setFunctionId(String functionId) {
        this.functionId = functionId;
    }

    public String getProgramName() {
        return programName;
    }

    public void setProgramName(String programName) {
        this.programName = programName;
    }

    public String getFunctionGroup() {
        return functionGroup;
    }

    public void setFunctionGroup(String functionGroup) {
        this.functionGroup = functionGroup;
    }

    public String getFunctionCategory() {
        return functionCategory;
    }

    public void setFunctionCategory(String functionCategory) {
        this.functionCategory = functionCategory;
    }

    public String getComponentGroup() {
        return componentGroup;
    }

    public void setComponentGroup(String componentGroup) {
        this.componentGroup = componentGroup;
    }

    public String getAuthorityRequired() {
        return authorityRequired;
    }

    public void setAuthorityRequired(String authorityRequired) {
        this.authorityRequired = authorityRequired;
    }

    public String getChangedBy() {
        return changedBy;
    }

    public void setChangedBy(String changedBy) {
        this.changedBy = changedBy;
    }

    public String getChangeDate() {
        return changeDate;
    }

    public void setChangeDate(String changeDate) {
        this.changeDate = changeDate;
    }
}
