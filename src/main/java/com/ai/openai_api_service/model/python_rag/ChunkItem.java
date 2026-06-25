package com.ai.openai_api_service.model.python_rag;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChunkItem {

    private String chunk;
    private Float score;
    private String title;
    private String source;

    @JsonProperty("programIds")
    private List<String> programIds;

    @JsonProperty("queryCount")
    private Integer queryCount;

    private String deliverable;

    @JsonProperty("docVersion")
    private String docVersion;
}
