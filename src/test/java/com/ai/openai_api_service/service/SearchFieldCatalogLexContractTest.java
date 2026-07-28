package com.ai.openai_api_service.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchFieldCatalogLexContractTest {

    private final SearchFieldCatalog catalog = new SearchFieldCatalog();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void everyManifestSlotMapsToExpectedM3Field() throws Exception {
        List<Map<String, String>> rows = loadManifest();
        for (Map<String, String> row : rows) {
            String intent = row.get("intent");
            String lexSlot = row.get("lexSlot");
            String expectedM3 = row.get("m3Field");
            var definition = catalog.findBySlot(intent, lexSlot);
            assertTrue(
                    definition.isPresent(),
                    () -> "Missing catalog mapping: intent=" + intent + " lexSlot=" + lexSlot
            );
            assertEquals(expectedM3, definition.get().m3Field(), intent + "/" + lexSlot);
        }
    }

    private List<Map<String, String>> loadManifest() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/lex-search-slot-manifest.json")) {
            if (in == null) {
                throw new IllegalStateException("lex-search-slot-manifest.json not found");
            }
            return objectMapper.readValue(in, new TypeReference<>() {
            });
        }
    }
}
