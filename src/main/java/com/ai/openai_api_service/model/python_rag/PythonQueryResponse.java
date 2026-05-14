package com.ai.openai_api_service.model.python_rag;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PythonQueryResponse {

    private String answer;
    private List<SourceItem> sources;
    private String model;

    @JsonProperty("retrievedChunks")
    private Integer retrievedChunks;

    private UsageInfo usage;
}
