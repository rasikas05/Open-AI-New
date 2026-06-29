package com.ai.openai_api_service.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OpenAIUsage {

    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private String model;
}
