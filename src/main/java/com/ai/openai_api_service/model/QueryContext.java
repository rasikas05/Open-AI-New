package com.ai.openai_api_service.model;

import java.util.List;
import java.util.Map;

/**
 * Consolidated query state for M3 request building (incremental M3 query architecture).
 * Enrichment fields are optional until populated by {@code QueryUnderstander}.
 */
public record QueryContext(
        String intentName,
        Map<String, String> slots,
        List<SearchCriterion> criteria,
        List<String> requestedInformation,
        Integer limit,
        List<String> returnColumns,
        String positionKey,
        String sort,
        String aggregation,
        Map<String, String> filters,
        boolean continuationRequested
) {
    public QueryContext {
        slots = slots != null ? Map.copyOf(slots) : Map.of();
        criteria = criteria != null ? List.copyOf(criteria) : List.of();
        requestedInformation = requestedInformation != null ? List.copyOf(requestedInformation) : List.of();
        returnColumns = returnColumns != null ? List.copyOf(returnColumns) : List.of();
        filters = filters != null ? Map.copyOf(filters) : Map.of();
    }

    public static QueryContext forSearch(
            String intentName,
            Map<String, String> slots,
            List<SearchCriterion> criteria
    ) {
        return new QueryContext(
                intentName,
                slots,
                criteria,
                List.of(),
                null,
                List.of(),
                null,
                null,
                null,
                Map.of(),
                false
        );
    }

    public static QueryContext forRead(String intentName, Map<String, String> slots) {
        return new QueryContext(
                intentName,
                slots,
                List.of(),
                List.of(),
                null,
                List.of(),
                null,
                null,
                null,
                Map.of(),
                false
        );
    }

    public static QueryContext withCriteria(List<SearchCriterion> criteria) {
        return new QueryContext(
                null,
                Map.of(),
                criteria,
                List.of(),
                null,
                List.of(),
                null,
                null,
                null,
                Map.of(),
                false
        );
    }

    public QueryContext withRequestedInformation(List<String> requestedInformation) {
        return new QueryContext(
                intentName,
                slots,
                criteria,
                requestedInformation,
                limit,
                returnColumns,
                positionKey,
                sort,
                aggregation,
                filters,
                continuationRequested
        );
    }

    public QueryContext withLimit(Integer limit) {
        return new QueryContext(
                intentName,
                slots,
                criteria,
                requestedInformation,
                limit,
                returnColumns,
                positionKey,
                sort,
                aggregation,
                filters,
                continuationRequested
        );
    }

    public QueryContext withReturnColumns(List<String> returnColumns) {
        return new QueryContext(
                intentName,
                slots,
                criteria,
                requestedInformation,
                limit,
                returnColumns,
                positionKey,
                sort,
                aggregation,
                filters,
                continuationRequested
        );
    }

    public QueryContext withPositionKey(String positionKey) {
        return new QueryContext(
                intentName,
                slots,
                criteria,
                requestedInformation,
                limit,
                returnColumns,
                positionKey,
                sort,
                aggregation,
                filters,
                continuationRequested
        );
    }

    public QueryContext withContinuationRequested(boolean continuationRequested) {
        return new QueryContext(
                intentName,
                slots,
                criteria,
                requestedInformation,
                limit,
                returnColumns,
                positionKey,
                sort,
                aggregation,
                filters,
                continuationRequested
        );
    }
}
