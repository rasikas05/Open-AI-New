package com.ai.openai_api_service.model.python_rag;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class M3ExecuteResponse {

    private String tool;
    private String reply;

    @JsonProperty("action_taken")
    private String actionTaken;

    @JsonProperty("m3_data")
    private Map<String, Object> m3Data;

    private String error;
}
