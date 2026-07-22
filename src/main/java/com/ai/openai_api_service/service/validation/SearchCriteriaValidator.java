package com.ai.openai_api_service.service.validation;

import com.ai.openai_api_service.model.SearchCriterion;

import java.util.List;

/**
 * Validates that search intents have at least one usable criterion before M3 execution.
 */
public final class SearchCriteriaValidator {

    public static final String NO_CRITERIA_MESSAGE =
            "Unable to process the request because no search criteria were provided. "
                    + "Please update your request with a valid search criterion.";

    public static final String ACTION_SEARCH_CRITERIA_MISSING = "search_criteria_missing";

    private SearchCriteriaValidator() {
    }

    public static boolean hasUsableCriteria(List<SearchCriterion> criteria) {
        if (criteria == null || criteria.isEmpty()) {
            return false;
        }
        for (SearchCriterion criterion : criteria) {
            if (criterion == null) {
                continue;
            }
            String field = criterion.field();
            String value = criterion.value();
            if (field != null && !field.isBlank() && value != null && !value.isBlank()) {
                return true;
            }
        }
        return false;
    }
}
