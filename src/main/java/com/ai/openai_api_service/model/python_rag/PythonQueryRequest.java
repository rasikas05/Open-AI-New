package com.ai.openai_api_service.model.python_rag;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PythonQueryRequest {

    private String question;

    @JsonProperty("top_k")
    private Integer topK = 5;

    @JsonProperty("final_limit")
    private Integer finalLimit = 8;

    private String deliverable;

    @JsonProperty("program_ids")
    private List<String> programIds;

    @JsonProperty("doc_version")
    private String docVersion;

    @JsonProperty("skip_rewrite")
    private Boolean skipRewrite = false;
}
