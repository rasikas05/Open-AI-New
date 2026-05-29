package com.ai.openai_api_service.model.python_rag;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UsageInfo {

    @JsonProperty("promptTokens")
    private Integer promptTokens;

    @JsonProperty("completionTokens")
    private Integer completionTokens;

    @JsonProperty("totalTokens")
    private Integer totalTokens;
}
