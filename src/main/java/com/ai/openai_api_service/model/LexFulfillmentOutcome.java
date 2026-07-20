package com.ai.openai_api_service.model;

import java.util.List;

public record LexFulfillmentOutcome(
        ChatResponse response,
        List<SearchCriterion> searchCriteria
) {
    public LexFulfillmentOutcome {
        searchCriteria = searchCriteria != null ? List.copyOf(searchCriteria) : List.of();
    }
}
