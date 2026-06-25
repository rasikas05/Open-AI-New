package com.ai.openai_api_service.model.python_rag;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PythonRetrievalResponse {

    private String query;
    private Integer total;
    private List<ChunkItem> results;

    @JsonProperty("promptChunks")
    private List<ChunkItem> promptChunks;

    @JsonProperty("ragAccepted")
    private Boolean ragAccepted;

    @JsonProperty("acceptThreshold")
    private Float acceptThreshold;

    @JsonProperty("promptThreshold")
    private Float promptThreshold;

    @JsonProperty("promptChunkCount")
    private Integer promptChunkCount;

    @JsonProperty("maxScore")
    private Float maxScore;

    @JsonProperty("rewrittenQueries")
    private List<String> rewrittenQueries;

    @JsonProperty("retrievalReason")
    private String retrievalReason;

    @JsonProperty("retrievalTimeMs")
    private Integer retrievalTimeMs;

    private String error;
}
