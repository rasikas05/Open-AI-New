package com.ai.openai_api_service.model.python_rag;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class M3MiCallRequest {

    private String program;
    private String transaction;
    private Map<String, Object> params;

    private String company;

    @JsonProperty("max_returned_records")
    private Integer maxReturnedRecords;

    public M3MiCallRequest(String program, String transaction, Map<String, Object> params) {
        this.program = program;
        this.transaction = transaction;
        this.params = params;
    }
}
