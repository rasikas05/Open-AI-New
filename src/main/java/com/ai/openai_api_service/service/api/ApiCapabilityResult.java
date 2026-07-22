package com.ai.openai_api_service.service.api;

import java.util.LinkedHashSet;
import java.util.List;

public record ApiCapabilityResult(
        boolean shouldExecuteM3,
        LinkedHashSet<String> supportedReturnColumns,
        List<String> supportedCodes,
        List<String> unsupportedCodes,
        String userMessage,
        String actionTaken
) {
    public static ApiCapabilityResult fullExecute() {
        return new ApiCapabilityResult(
                true,
                new LinkedHashSet<>(),
                List.of(),
                List.of(),
                null,
                null
        );
    }

    public static ApiCapabilityResult blocked(String message, String actionTaken) {
        return new ApiCapabilityResult(
                false,
                new LinkedHashSet<>(),
                List.of(),
                List.of(),
                message,
                actionTaken
        );
    }

    public static ApiCapabilityResult partialExecute(
            LinkedHashSet<String> columns,
            List<String> supportedCodes,
            List<String> unsupportedCodes,
            String message
    ) {
        return new ApiCapabilityResult(
                true,
                columns,
                List.copyOf(supportedCodes),
                List.copyOf(unsupportedCodes),
                message,
                null
        );
    }
}
