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
public class M3MiCallResponse {

    private String program;
    private String transaction;
    private Map<String, Object> raw;

    @JsonProperty("error_message")
    private String errorMessage;

    private List<Map<String, Object>> records;
    private String error;
}
