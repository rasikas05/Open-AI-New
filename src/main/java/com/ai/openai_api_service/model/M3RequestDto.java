package com.ai.openai_api_service.model;

import java.util.Map;

public class M3RequestDto {

    private boolean execute;
    private String program;
    private String transaction;
    private Map<String, Object> params;

    public M3RequestDto() {
    }

    public M3RequestDto(boolean execute, String program, String transaction, Map<String, Object> params) {
        this.execute = execute;
        this.program = program;
        this.transaction = transaction;
        this.params = params;
    }

    public boolean isExecute() {
        return execute;
    }

    public void setExecute(boolean execute) {
        this.execute = execute;
    }

    public String getProgram() {
        return program;
    }

    public void setProgram(String program) {
        this.program = program;
    }

    public String getTransaction() {
        return transaction;
    }

    public void setTransaction(String transaction) {
        this.transaction = transaction;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public void setParams(Map<String, Object> params) {
        this.params = params;
    }
}
