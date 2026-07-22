package com.ai.openai_api_service.model;

import java.util.List;

public record LexFulfillmentOutcome(
        ChatResponse response,
        List<SearchCriterion> searchCriteria,
        QueryContext queryContext,
        SearchContext searchContext
) {
    public LexFulfillmentOutcome {
        searchCriteria = searchCriteria != null ? List.copyOf(searchCriteria) : List.of();
    }

    public LexFulfillmentOutcome(ChatResponse response, List<SearchCriterion> searchCriteria) {
        this(response, searchCriteria, null, null);
    }
}
