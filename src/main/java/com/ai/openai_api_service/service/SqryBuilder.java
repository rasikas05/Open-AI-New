package com.ai.openai_api_service.service;

import com.ai.openai_api_service.model.SearchCriterion;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class SqryBuilder {

    private final SearchValueFormatter searchValueFormatter;

    public SqryBuilder(SearchValueFormatter searchValueFormatter) {
        this.searchValueFormatter = searchValueFormatter;
    }

    public String build(List<SearchCriterion> criteria) {
        if (criteria == null || criteria.isEmpty()) {
            return "";
        }

        List<String> fragments = new ArrayList<>();
        for (SearchCriterion criterion : criteria) {
            if (criterion == null) {
                continue;
            }

            String field = criterion.field();
            String value = criterion.value();
            if (field == null || field.isBlank() || value == null || value.isBlank()) {
                continue;
            }

            String formatted = searchValueFormatter.format(field, value.trim());
            if (formatted == null || formatted.isBlank()) {
                continue;
            }

            fragments.add(field + ":" + formatted);
        }
        return String.join(" AND ", fragments);
    }
}
