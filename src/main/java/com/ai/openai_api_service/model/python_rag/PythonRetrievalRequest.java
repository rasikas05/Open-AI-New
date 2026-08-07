package com.ai.openai_api_service.model.python_rag;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PythonRetrievalRequest {

    private String query;

    @JsonProperty("top_k")
    private Integer topK = 5;

    @JsonProperty("final_limit")
    private Integer finalLimit = 8;

    private String deliverable;

    @JsonProperty("program_ids")
    private List<String> programIds;

    /** Soft-boost only — never used as a hard Qdrant filter. */
    @JsonProperty("boost_program_ids")
    private List<String> boostProgramIds;

    /** Amount added once to rankScore when a chunk matches boost_program_ids. */
    @JsonProperty("program_boost")
    private Double programBoost;

    @JsonProperty("doc_version")
    private String docVersion;

    @JsonProperty("skip_rewrite")
    private Boolean skipRewrite = false;

    @JsonProperty("queries")
    private List<String> queries;

    /** Correlation id for Python stage timing logs (optional). */
    @JsonProperty("request_id")
    private String requestId;

    /** Chat/session id for correlating Spring and Python logs (optional). */
    @JsonProperty("conversation_id")
    private String conversationId;
}
