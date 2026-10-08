package com.ai.openai_api_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Dedicated executor for parallel RAG suggestion OpenAI calls (Step 2).
 * Virtual threads keep suggestion work off the request thread without a large pool.
 */
@Configuration
public class SuggestionExecutorConfig {

    public static final String SUGGESTION_EXECUTOR_BEAN = "suggestionExecutor";

    @Bean(name = SUGGESTION_EXECUTOR_BEAN, destroyMethod = "close")
    public ExecutorService suggestionExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
