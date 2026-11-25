package com.ai.openai_api_service.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class OpenAIUsage {

    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private String model;

    /** Present only when Bedrock/OpenAI returned {@code usage.prompt_tokens_details}. */
    private Integer cacheWriteTokens;
    /** Present only when Bedrock/OpenAI returned {@code usage.prompt_tokens_details}. */
    private Integer cachedTokens;
    /** True only when {@code prompt_tokens_details} was present in the usage object. */
    private boolean cacheMetricsPresent;

    /** Compatibility constructor used across the codebase (cache fields unset / not present). */
    public OpenAIUsage(Integer promptTokens, Integer completionTokens, Integer totalTokens, String model) {
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = totalTokens;
        this.model = model;
        this.cacheMetricsPresent = false;
    }
}
