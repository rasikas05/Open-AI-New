package com.ai.openai_api_service.service.repair;

import com.ai.openai_api_service.model.SearchFieldDefinition;
import com.ai.openai_api_service.service.SearchFieldCatalog;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SlotKeywordRegistry {

    private final SearchFieldCatalog searchFieldCatalog;
    private final Map<String, Set<String>> keywordTextsCache = new ConcurrentHashMap<>();

    public SlotKeywordRegistry(SearchFieldCatalog searchFieldCatalog) {
        this.searchFieldCatalog = searchFieldCatalog;
    }

    public List<KeywordMapping> keywordsForIntent(String intentName) {
        if (intentName == null || intentName.isBlank()) {
            return List.of();
        }

        List<KeywordMapping> mappings = new ArrayList<>();
        for (SearchFieldDefinition field : searchFieldCatalog.fieldsFor(intentName)) {
            if (field.lexSlotName() == null || field.lexSlotName().isBlank()) {
                continue;
            }
            for (String keyword : field.keywords()) {
                if (keyword == null || keyword.isBlank()) {
                    continue;
                }
                mappings.add(new KeywordMapping(
                        keyword.trim().toLowerCase(Locale.ROOT),
                        field.lexSlotName(),
                        field.m3Field()
                ));
            }
        }

        mappings.sort(Comparator.comparingInt((KeywordMapping m) -> m.keyword().length()).reversed());
        return List.copyOf(mappings);
    }

    /**
     * All keyword phrases for an intent (lowercase), used to reject captured values that are field labels.
     */
    public Set<String> keywordTextsForIntent(String intentName) {
        if (intentName == null || intentName.isBlank()) {
            return Set.of();
        }
        return keywordTextsCache.computeIfAbsent(intentName, this::buildKeywordTextsForIntent);
    }

    private Set<String> buildKeywordTextsForIntent(String intentName) {
        Set<String> texts = new HashSet<>();
        for (KeywordMapping mapping : keywordsForIntent(intentName)) {
            texts.add(mapping.keyword());
        }
        return Set.copyOf(texts);
    }

    public record KeywordMapping(String keyword, String lexSlotName, String m3Field) {
    }
}
