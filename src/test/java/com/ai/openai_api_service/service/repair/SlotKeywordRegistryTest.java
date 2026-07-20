package com.ai.openai_api_service.service.repair;

import com.ai.openai_api_service.service.SearchFieldCatalog;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlotKeywordRegistryTest {

    private final SlotKeywordRegistry registry = new SlotKeywordRegistry(new SearchFieldCatalog());

    @Test
    void keywordsForIntent_includesSupplierAndWarehouse() {
        var keywords = registry.keywordsForIntent("SearchPurchaseOrder");

        assertTrue(keywords.stream().anyMatch(k -> "supplier".equals(k.keyword()) && "Supplier".equals(k.lexSlotName())));
        assertTrue(keywords.stream().anyMatch(k -> "warehouse".equals(k.keyword()) && "Warehouse".equals(k.lexSlotName())));
    }

    @Test
    void keywordsForIntent_longerKeywordsFirst() {
        var keywords = registry.keywordsForIntent("SearchCustomerOrder");

        assertTrue(keywords.size() >= 2);
        assertTrue(keywords.getFirst().keyword().length() >= keywords.getLast().keyword().length());
    }

    @Test
    void keywordsForIntent_unknownIntent_returnsEmpty() {
        assertTrue(registry.keywordsForIntent("Unknown").isEmpty());
    }

    @Test
    void keywordTextsForIntent_includesCatalogKeywordsLowercase() {
        var texts = registry.keywordTextsForIntent("SearchCustomerOrder");

        assertTrue(texts.contains("customer"));
        assertTrue(texts.contains("customer order"));
        assertTrue(texts.contains("order"));
    }
}
