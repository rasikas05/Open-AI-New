package com.ai.openai_api_service.service;

import com.ai.openai_api_service.model.SearchCriterion;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class SearchResolver {

    private final SearchFieldCatalog searchFieldCatalog;

    public SearchResolver(SearchFieldCatalog searchFieldCatalog) {
        this.searchFieldCatalog = searchFieldCatalog;
    }

    public List<SearchCriterion> resolve(String intentName, Map<String, String> slots) {
        if (intentName == null || intentName.isBlank() || slots == null) {
            return List.of();
        }

        List<SearchCriterion> criteria = new ArrayList<>();
        for (Map.Entry<String, String> entry : slots.entrySet()) {
            String value = entry.getValue();
            if (value == null || value.isBlank()) {
                continue;
            }

            searchFieldCatalog.findBySlot(intentName, entry.getKey())
                    .or(() -> searchFieldCatalog.find(intentName, entry.getKey()))
                    .ifPresent(definition ->
                            criteria.add(new SearchCriterion(definition.m3Field(), value.trim())));
        }
        return List.copyOf(criteria);
    }
}
