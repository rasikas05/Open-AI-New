package com.ai.openai_api_service.service.validation;

import com.ai.openai_api_service.model.SearchCriterion;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchCriteriaValidatorTest {

    @Test
    void hasUsableCriteria_nullOrEmpty_returnsFalse() {
        assertFalse(SearchCriteriaValidator.hasUsableCriteria(null));
        assertFalse(SearchCriteriaValidator.hasUsableCriteria(List.of()));
    }

    @Test
    void hasUsableCriteria_blankFieldOrValue_returnsFalse() {
        assertFalse(SearchCriteriaValidator.hasUsableCriteria(
                List.of(new SearchCriterion("", "C00001"))
        ));
        assertFalse(SearchCriteriaValidator.hasUsableCriteria(
                List.of(new SearchCriterion("CUNO", "   "))
        ));
        assertFalse(SearchCriteriaValidator.hasUsableCriteria(
                List.of(new SearchCriterion(null, "C00001"))
        ));
    }

    @Test
    void hasUsableCriteria_oneValidCriterion_returnsTrue() {
        assertTrue(SearchCriteriaValidator.hasUsableCriteria(
                List.of(new SearchCriterion("CUNO", "C00001"))
        ));
    }

    @Test
    void hasSearchableCriteria_delegatesToHasUsableCriteria() {
        assertFalse(SearchCriteriaValidator.hasSearchableCriteria(null));
        assertTrue(SearchCriteriaValidator.hasSearchableCriteria(
                List.of(new SearchCriterion("ORNO", "1000001234"))
        ));
        assertEquals(1, SearchCriteriaValidator.searchableCriteriaCount(
                List.of(new SearchCriterion("ORNO", "1000001234"))
        ));
    }
}
